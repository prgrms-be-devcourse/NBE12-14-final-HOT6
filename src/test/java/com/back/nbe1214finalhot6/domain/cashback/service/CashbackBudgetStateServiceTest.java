package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackBudgetStateLog;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.StateChangeReason;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackBudgetStateLogRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적립 중단 상태 전이. 11월 배정 1,000,000 이면 대비금(공지선)은 100,000, 긴급선은 50,000 이다. 테스트 시계는 2026-11-18 09:00 KST 이고 공지 후 중단
 * 예약 시각은 판정일 + 3일 00:00 이다.
 */
@IntegrationTest
@Transactional
class CashbackBudgetStateServiceTest {

  private static final int YEAR = 2026;
  private static final int MONTH = 11;

  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackBudgetStateService stateService;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  @Autowired private CashbackBudgetStateLogRepository stateLogRepository;
  @Autowired private Clock clock;
  @PersistenceContext private EntityManager entityManager;

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(YEAR, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(
        YEAR, MONTH, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
    budgetService.putMonthly(
        YEAR, 12, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  private BudgetStatus status(int month) {
    entityManager.flush();
    return monthlyBudgetRepository.findStatus(YEAR, month).orElseThrow();
  }

  private List<CashbackBudgetStateLog> logs(int month) {
    entityManager.flush();
    Long id = monthlyBudgetRepository.findId(YEAR, month).orElseThrow();
    return stateLogRepository.findAllByMonthlyBudgetIdOrderByIdAsc(id);
  }

  private LocalDateTime stopScheduledAt(int month) {
    entityManager.flush();
    entityManager.clear();
    CashbackMonthlyBudget budget =
        monthlyBudgetRepository.findByBudgetYearAndBudgetMonth(YEAR, month).orElseThrow();
    return budget.getStopScheduledAt();
  }

  // 목적: 남은 예산이 공지선 바로 위에서는 전이가 없고, 딱 공지선(대비금)에 닿으면 공지 상태가 되며 중단 예약 시각(판정일 + 3일 00:00)과 이력(자동 판정,
  // 당시 남은 비율)이 남는지 확인한다.
  @Test
  @DisplayName("남은 예산이 딱 공지선에 닿으면 공지 상태가 되고 예약 시각과 이력이 남는다")
  void noticeLine_boundary() {
    budgetService.reserveBudget(YEAR, MONTH, 899_999L);
    assertThat(status(MONTH)).isEqualTo(BudgetStatus.ACTIVE);
    assertThat(logs(MONTH)).isEmpty();

    budgetService.reserveBudget(YEAR, MONTH, 1L);

    assertThat(status(MONTH)).isEqualTo(BudgetStatus.NOTICED);
    assertThat(stopScheduledAt(MONTH)).isEqualTo(LocalDateTime.of(2026, 11, 21, 0, 0));
    CashbackBudgetStateLog log = logs(MONTH).get(0);
    assertThat(logs(MONTH)).hasSize(1);
    assertThat(log.getFromStatus()).isEqualTo(BudgetStatus.ACTIVE);
    assertThat(log.getToStatus()).isEqualTo(BudgetStatus.NOTICED);
    assertThat(log.getReason()).isEqualTo(StateChangeReason.AUTO_NOTICE_LINE);
    assertThat(log.getRemainingRate()).isEqualByComparingTo("0.1");
    assertThat(log.getOperatorId()).isNull();
    assertThat(log.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 0));
  }

  // 목적: 공지 상태에서 남은 예산이 긴급선(대비금의 절반) 바로 위에서는 그대로이고, 딱 긴급선에 닿으면 중단되는지(NOTICED -> STOPPED) 확인한다.
  @Test
  @DisplayName("공지 상태에서 남은 예산이 딱 긴급선에 닿으면 중단된다")
  void emergencyLine_fromNoticed() {
    budgetService.reserveBudget(YEAR, MONTH, 900_000L);
    budgetService.reserveBudget(YEAR, MONTH, 49_999L);
    assertThat(status(MONTH)).isEqualTo(BudgetStatus.NOTICED);

    budgetService.reserveBudget(YEAR, MONTH, 1L);

    assertThat(status(MONTH)).isEqualTo(BudgetStatus.STOPPED);
    List<CashbackBudgetStateLog> logs = logs(MONTH);
    assertThat(logs).hasSize(2);
    assertThat(logs.get(1).getFromStatus()).isEqualTo(BudgetStatus.NOTICED);
    assertThat(logs.get(1).getToStatus()).isEqualTo(BudgetStatus.STOPPED);
    assertThat(logs.get(1).getReason()).isEqualTo(StateChangeReason.AUTO_EMERGENCY_LINE);
    assertThat(logs.get(1).getRemainingRate()).isEqualByComparingTo("0.05");
  }

  // 목적: 한 번의 확보로 공지선을 건너뛰고 긴급선 아래로 내려가면 공지 없이 바로 중단되고(ACTIVE -> STOPPED), 이력이 한 건만 남는지 확인한다.
  @Test
  @DisplayName("공지선을 건너뛰고 긴급선 아래로 내려가면 바로 중단된다")
  void emergencyLine_skippingNotice() {
    budgetService.reserveBudget(YEAR, MONTH, 960_000L);

    assertThat(status(MONTH)).isEqualTo(BudgetStatus.STOPPED);
    assertThat(stopScheduledAt(MONTH)).isNull();
    List<CashbackBudgetStateLog> logs = logs(MONTH);
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getFromStatus()).isEqualTo(BudgetStatus.ACTIVE);
    assertThat(logs.get(0).getToStatus()).isEqualTo(BudgetStatus.STOPPED);
    assertThat(logs.get(0).getReason()).isEqualTo(StateChangeReason.AUTO_EMERGENCY_LINE);
  }

  // 목적: 중단된 달은 이후 확보가 모자람이 아니라 중단(NOT_ACTIVE)으로 거절되고, 다른 달·다른 판정에는 영향이 없으며, 복원해도 상태가 되돌아가지 않는지
  // 확인한다(새 월 예산이 열릴 때만 복귀).
  @Test
  @DisplayName("중단된 달은 확보를 거절하고 복원해도 되돌아가지 않으며 다른 달은 그대로다")
  void stopped_staysStopped() {
    budgetService.reserveBudget(YEAR, MONTH, 960_000L);

    assertThat(budgetService.reserveBudget(YEAR, MONTH, 1_000L))
        .isEqualTo(BudgetReserveResult.NOT_ACTIVE);
    budgetService.releaseBudget(YEAR, MONTH, 500_000L);

    assertThat(status(MONTH)).isEqualTo(BudgetStatus.STOPPED);
    assertThat(logs(MONTH)).hasSize(1);
    assertThat(status(12)).isEqualTo(BudgetStatus.ACTIVE);
    assertThat(budgetService.reserveBudget(YEAR, 12, 1_000L)).isEqualTo(BudgetReserveResult.RESERVED);
  }

  // 목적: 예산이 모자라 확보가 실패하면 판정하지 않고(상태·이력 변화 없음), 예산이 없는 달은 오류 없이 NO_BUDGET 인지 확인한다.
  @Test
  @DisplayName("확보가 실패하면 상태를 바꾸지 않는다")
  void failedReserve_doesNotEvaluate() {
    budgetService.reserveBudget(YEAR, MONTH, 800_000L);

    assertThat(budgetService.reserveBudget(YEAR, MONTH, 200_001L))
        .isEqualTo(BudgetReserveResult.INSUFFICIENT);
    assertThat(budgetService.reserveBudget(YEAR, 3, 1_000L)).isEqualTo(BudgetReserveResult.NO_BUDGET);

    assertThat(status(MONTH)).isEqualTo(BudgetStatus.ACTIVE);
    assertThat(logs(MONTH)).isEmpty();
  }

  // 목적: 공지 후 예약 시각이 지나면 스케줄러 처리가 중단으로 바꾸고(이력은 예약 시각 도달), 시각 전에는 바꾸지 않으며, 다시 불러도 한 번만 처리되는지
  // 확인한다. 중단된 달에도 예약 시각이 남지만 상태가 공지가 아니므로 다시 잡히지 않는다.
  @Test
  @DisplayName("예약 시각이 지나면 중단하고 그 전에는 두며 다시 불러도 한 번만 처리한다")
  void scheduledStop() {
    budgetService.reserveBudget(YEAR, MONTH, 900_000L);
    assertThat(status(MONTH)).isEqualTo(BudgetStatus.NOTICED);

    ((MutableClock) clock).advance(Duration.ofDays(2));
    assertThat(stateService.stopDueScheduled()).isZero();
    assertThat(status(MONTH)).isEqualTo(BudgetStatus.NOTICED);

    // 2026-11-21 00:00 이후
    ((MutableClock) clock).advance(Duration.ofHours(16));
    assertThat(stateService.stopDueScheduled()).isEqualTo(1);
    assertThat(stateService.stopDueScheduled()).isZero();

    assertThat(status(MONTH)).isEqualTo(BudgetStatus.STOPPED);
    List<CashbackBudgetStateLog> logs = logs(MONTH);
    assertThat(logs).hasSize(2);
    assertThat(logs.get(1).getFromStatus()).isEqualTo(BudgetStatus.NOTICED);
    assertThat(logs.get(1).getToStatus()).isEqualTo(BudgetStatus.STOPPED);
    assertThat(logs.get(1).getReason()).isEqualTo(StateChangeReason.SCHEDULED_TIME);
    assertThat(logs.get(1).getRemainingRate()).isEqualByComparingTo("0.1");
  }
}
