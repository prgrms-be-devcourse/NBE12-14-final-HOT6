package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackBudgetStateLog;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.StateChangeReason;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
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

  /**
   * 운영자의 "공지 후 중단". 정상({@code ACTIVE})인 월 예산을 공지({@code NOTICED}) 상태로 바꾸고 중단 예약 시각을 판정일 + 공지 일수의 00:00 으로
   * 정한다. 이미 공지 상태면 예약 시각을 바꾸지 않고 그대로 둔다(이력도 남기지 않는다). 자동 판정과 같은 조건부 UPDATE 와 이력을 쓰고 사유만 수동으로 남긴다.
   *
   * @throws BusinessException 월이 올바르지 않을 때(CSH-007), 월 예산이 없을 때(CSH-004), 이미 중단됐거나 마감된 달일 때(CSH-012)
   */
  @Transactional
  public void noticeStopManually(int year, int month) {
    CashbackMonthlyBudget.validateMonth(month);
    BudgetStatus status = requireStatus(year, month);
    if (status == BudgetStatus.NOTICED) {
      return;
    }
    if (status != BudgetStatus.ACTIVE) {
      throw new BusinessException(CashbackErrorCode.BUDGET_ALREADY_STOPPED);
    }
    LocalDateTime now = LocalDateTime.now(clock);
    if (monthlyBudgetRepository.changeStatusWithSchedule(
            year, month, BudgetStatus.ACTIVE, BudgetStatus.NOTICED, scheduledStopAt(now))
        == 1) {
      record(year, month, BudgetStatus.ACTIVE, BudgetStatus.NOTICED,
          StateChangeReason.OPERATOR_NOTICE_STOP, now);
      return;
    }
    // 읽은 뒤에 다른 요청이 상태를 바꿨다. 이미 공지 상태면 같은 결과이고, 중단·마감됐으면 거절한다
    if (requireStatus(year, month) != BudgetStatus.NOTICED) {
      throw new BusinessException(CashbackErrorCode.BUDGET_ALREADY_STOPPED);
    }
  }

  /**
   * 운영자의 "긴급 중단". 정상이나 공지 상태의 월 예산을 즉시 중단({@code STOPPED})으로 바꾼다. 확인(confirm)은 컨트롤러가 요청 본문에서 검증한다.
   *
   * @throws BusinessException 월이 올바르지 않을 때(CSH-007), 월 예산이 없을 때(CSH-004), 이미 중단됐거나 마감된 달일 때(CSH-012). 같은 요청을 반복하면
   *     두 번째부터는 이 오류이며 이력은 한 번만 남는다
   */
  @Transactional
  public void emergencyStopManually(int year, int month) {
    CashbackMonthlyBudget.validateMonth(month);
    BudgetStatus status = requireStatus(year, month);
    if (status == BudgetStatus.STOPPED || status == BudgetStatus.CLOSED) {
      throw new BusinessException(CashbackErrorCode.BUDGET_ALREADY_STOPPED);
    }
    LocalDateTime now = LocalDateTime.now(clock);
    // 읽은 뒤에 자동 판정이 정상 -> 공지로 바꿨을 수 있어서 두 상태를 차례로 시도한다
    for (BudgetStatus from : List.of(BudgetStatus.ACTIVE, BudgetStatus.NOTICED)) {
      if (monthlyBudgetRepository.changeStatus(year, month, from, BudgetStatus.STOPPED) == 1) {
        record(year, month, from, BudgetStatus.STOPPED, StateChangeReason.OPERATOR_EMERGENCY_STOP, now);
        return;
      }
    }
    throw new BusinessException(CashbackErrorCode.BUDGET_ALREADY_STOPPED);
  }

  private BudgetStatus requireStatus(int year, int month) {
    return monthlyBudgetRepository
        .findStatus(year, month)
        .orElseThrow(() -> new BusinessException(CashbackErrorCode.BUDGET_NOT_FOUND));
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
    boolean byOperator =
        reason == StateChangeReason.OPERATOR_NOTICE_STOP
            || reason == StateChangeReason.OPERATOR_EMERGENCY_STOP;
    // 처리자는 인증이 머지되기 전까지 null 이다
    stateLogRepository.save(
        byOperator
            ? CashbackBudgetStateLog.byOperator(budgetId, from, to, reason, remainingRate, null, now)
            : CashbackBudgetStateLog.automatic(budgetId, from, to, reason, remainingRate, now));
  }
}
