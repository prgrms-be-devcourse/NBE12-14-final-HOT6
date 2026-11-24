package com.back.nbe1214finalhot6.domain.settlement.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.dto.SettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 운영자가 정산을 직접 실행할 때 거치는 서비스. 스케줄러와 같은 순서를 지켜서, 정산일 사이에 빈 구간이 생기거나 일 정산이 덜 끝난 달의 부족한 합계로 월 지급이
 * 확정되지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class SettlementRunService {

  private final DailySettlementService dailySettlementService;
  private final MonthlySettlementService monthlySettlementService;
  private final Clock clock;

  /**
   * 가장 최근 정산일부터 date 까지 날짜 순서대로 정산하고 결과를 하나로 합친다. 한 날짜만 정산하면 그 사이 날짜가 영영 비고 전일 이월이 끊기므로 {@code
   * settle} 이 아니라 {@code settleUntil} 을 거친다. 실패한 가맹점이 있는 날짜에서 멈추며, 돌려주는 날짜는 마지막으로 정산한 날짜다.
   */
  public SettlementRunResult runDaily(LocalDate date) {
    List<SettlementRunResult> results = dailySettlementService.settleUntil(date);
    return new SettlementRunResult(
        results.getLast().date(),
        results.stream().mapToInt(SettlementRunResult::created).sum(),
        results.stream().mapToInt(SettlementRunResult::skipped).sum(),
        results.stream().mapToInt(SettlementRunResult::failed).sum());
  }

  /**
   * 그 달 말일까지 일 정산을 먼저 채우고, 실패한 날이 있으면 월 정산 건을 만들지 않고 막는다. 월 정산 건은 한 번 만들면 금액을 고치지 않고 지급까지 바로
   * 끝나서, 일 정산이 덜 끝난 합계로 만들면 부족분을 지급할 방법이 없다. 스케줄러는 지켜보는 사람이 없어 대기 건만 지급하고 넘어가지만, 수동 실행은 운영자가
   * 결과를 바로 보므로 막은 이유를 알려 준다.
   *
   * @throws BusinessException 이번 달 이후이면 STL-003, 일 정산이 끝나지 않았으면 STL-005
   */
  public MonthlySettlementRunResult runMonthly(YearMonth month) {
    if (!month.isBefore(YearMonth.now(clock))) {
      throw new BusinessException(SettlementErrorCode.SETTLEMENT_MONTH_NOT_ALLOWED);
    }
    List<SettlementRunResult> daily = dailySettlementService.settleUntil(month.atEndOfMonth());
    if (daily.stream().anyMatch(result -> result.failed() > 0)) {
      throw new BusinessException(SettlementErrorCode.DAILY_SETTLEMENT_INCOMPLETE);
    }
    return monthlySettlementService.settle(month);
  }
}
