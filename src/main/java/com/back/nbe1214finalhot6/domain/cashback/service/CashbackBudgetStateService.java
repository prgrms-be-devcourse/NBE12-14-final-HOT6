package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackBudgetStateLog;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.entity.StateChangeReason;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackBudgetStateLogRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.MonthlyBudgetSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월 예산의 적립 중단 상태 전이. 예산이 줄면 정상({@code ACTIVE}) → 공지({@code NOTICED}) → 중단({@code STOPPED}) 순으로 적립을 멈춘다. 중단돼도
 * 결제는 정상이고 적립만 0원이 된다. 캐시백 도메인 내부 전용이다.
 *
 * <ul>
 *   <li>{@code ACTIVE → NOTICED}: 남은 예산 ≤ 대비금(공지선). 중단 예약 시각은 판정일 + 공지 일수 00:00. 적립은 계속한다
 *   <li>{@code NOTICED → STOPPED}: 예약 시각 도달, 또는 남은 예산 ≤ 대비금 × 긴급선 비율
 *   <li>{@code ACTIVE → STOPPED}: 공지선을 건너뛰고 남은 예산이 긴급선 이하가 된 경우 즉시 중단
 * </ul>
 *
 * <p>모든 전이는 {@code where status = <현재 상태>} 조건부 UPDATE 한 문장이라 같은 판정이 동시에 와도 한 번만 일어나고, 바뀐 행이 1건일 때만 이력을
 * 남긴다. 되돌리는 전이는 없다. 중단된 달의 다음 달은 새 월 예산이 정상으로 열려서 자동으로 복귀한다.
 */
@Service
@RequiredArgsConstructor
public class CashbackBudgetStateService {

  private final CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  private final CashbackBudgetStateLogRepository stateLogRepository;
  private final CashbackProperties properties;
  private final Clock clock;

  /**
   * 예산을 확보한 직후 중단 상태를 판정한다. 예산 확보와 같은 트랜잭션에서 부르며, 호출한 쪽의 트랜잭션에 참여한다. 한 번에 한 단계만 전이하고, 긴급선을
   * 먼저 본다.
   */
  @Transactional
  public void evaluateAfterReserve(int year, int month) {
    LocalDateTime now = LocalDateTime.now(clock);
    BigDecimal ratio = properties.emergencyRatio();
    if (monthlyBudgetRepository.stopIfBelowEmergency(
            year, month, BudgetStatus.ACTIVE, BudgetStatus.STOPPED, ratio)
        == 1) {
      record(year, month, BudgetStatus.ACTIVE, BudgetStatus.STOPPED,
          StateChangeReason.AUTO_EMERGENCY_LINE, now);
    } else if (monthlyBudgetRepository.stopIfBelowEmergency(
            year, month, BudgetStatus.NOTICED, BudgetStatus.STOPPED, ratio)
        == 1) {
      record(year, month, BudgetStatus.NOTICED, BudgetStatus.STOPPED,
          StateChangeReason.AUTO_EMERGENCY_LINE, now);
    } else if (monthlyBudgetRepository.noticeIfBelowNoticeLine(
            year, month, BudgetStatus.ACTIVE, BudgetStatus.NOTICED, scheduledStopAt(now))
        == 1) {
      record(year, month, BudgetStatus.ACTIVE, BudgetStatus.NOTICED,
          StateChangeReason.AUTO_NOTICE_LINE, now);
    }
  }

  /**
   * 중단 예약 시각이 지난 공지 상태의 월 예산을 중단으로 바꾼다. 스케줄러가 주기적으로 부른다.
   *
   * @return 중단으로 바꾼 월 예산 수
   */
  @Transactional
  public int stopDueScheduled() {
    LocalDateTime now = LocalDateTime.now(clock);
    List<Long> dueIds = monthlyBudgetRepository.findDueForStop(now, BudgetStatus.NOTICED);
    int stopped = 0;
    for (Long id : dueIds) {
      if (monthlyBudgetRepository.stopIfScheduledTimeReached(
              id, now, BudgetStatus.NOTICED, BudgetStatus.STOPPED)
          == 1) {
        record(id, BudgetStatus.NOTICED, BudgetStatus.STOPPED, StateChangeReason.SCHEDULED_TIME, now);
        stopped++;
      }
    }
    return stopped;
  }

  /** 중단 예약 시각 = 판정일 + 공지 일수의 00:00(KST). */
  private LocalDateTime scheduledStopAt(LocalDateTime now) {
    return now.toLocalDate().plusDays(properties.noticeDays()).atStartOfDay();
  }

  private void record(
      int year,
      int month,
      BudgetStatus from,
      BudgetStatus to,
      StateChangeReason reason,
      LocalDateTime now) {
    Long id = monthlyBudgetRepository.findId(year, month).orElseThrow();
    record(id, from, to, reason, now);
  }

  private void record(
      Long budgetId,
      BudgetStatus from,
      BudgetStatus to,
      StateChangeReason reason,
      LocalDateTime now) {
    MonthlyBudgetSnapshot snapshot = monthlyBudgetRepository.findSnapshot(budgetId).orElseThrow();
    BigDecimal remainingRate =
        snapshot.getTotal() <= 0
            ? BigDecimal.ZERO.setScale(CashbackPolicy.RATE_SCALE)
            : BigDecimal.valueOf(snapshot.getRemaining())
                .divide(
                    BigDecimal.valueOf(snapshot.getTotal()),
                    CashbackPolicy.RATE_SCALE,
                    RoundingMode.HALF_UP);
    stateLogRepository.save(
        CashbackBudgetStateLog.automatic(budgetId, from, to, reason, remainingRate, now));
  }
}
