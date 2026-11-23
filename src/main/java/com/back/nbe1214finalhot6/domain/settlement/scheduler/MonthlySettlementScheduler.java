package com.back.nbe1214finalhot6.domain.settlement.scheduler;

import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.dto.SettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.service.DailySettlementService;
import com.back.nbe1214finalhot6.domain.settlement.service.MonthlySettlementService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlySettlementScheduler {

  private final DailySettlementService dailySettlementService;
  private final MonthlySettlementService monthlySettlementService;
  private final Clock clock;

  // 매일 돌려도 이미 지급한 건은 건너뛴다. 1일에 서버가 꺼져 있었거나 실패한 건은 다음 날 이어서 처리된다.
  @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Seoul")
  public void settleLastMonth() {
    YearMonth lastMonth = YearMonth.now(clock).minusMonths(1);
    List<SettlementRunResult> daily =
        dailySettlementService.settleUntil(LocalDate.now(clock).minusDays(1));

    // 전월 말일까지의 일 정산이 실패했으면 전월 합계가 덜 모인 것이므로 새 건은 만들지 않고, 밀린 지급만 처리한다
    boolean lastMonthComplete =
        daily.stream()
            .noneMatch(
                result -> result.failed() > 0 && !result.date().isAfter(lastMonth.atEndOfMonth()));
    if (!lastMonthComplete) {
      log.warn("전월 일 정산이 끝나지 않아 월 정산 건 생성을 건너뛰고 대기 건만 지급합니다 month={}", lastMonth);
    }

    MonthlySettlementRunResult result =
        lastMonthComplete
            ? monthlySettlementService.settle(lastMonth)
            : monthlySettlementService.payPending(lastMonth);
    if (result.failed() > 0) {
      log.warn(
          "월 정산 일부 실패 month={}, created={}, paid={}, failed={}",
          result.month(),
          result.created(),
          result.paid(),
          result.failed());
    } else {
      log.info(
          "월 정산 완료 month={}, created={}, paid={}",
          result.month(),
          result.created(),
          result.paid());
    }
  }
}
