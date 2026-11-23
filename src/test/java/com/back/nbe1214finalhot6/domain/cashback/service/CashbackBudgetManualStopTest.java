package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackBudgetStateLog;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.StateChangeReason;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 운영자 수동 공지 후 중단·긴급 중단. 11월 배정 1,000,000. 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class CashbackBudgetManualStopTest {

  private static final int YEAR = 2026;
  private static final int MONTH = 11;

  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackBudgetStateService stateService;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  @Autowired private CashbackBudgetStateLogRepository stateLogRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Clock clock;
  @PersistenceContext private EntityManager entityManager;

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(YEAR, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(
        YEAR, MONTH, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  private BudgetStatus status() {
    entityManager.flush();
    return monthlyBudgetRepository.findStatus(YEAR, MONTH).orElseThrow();
  }

  private List<CashbackBudgetStateLog> logs() {
    entityManager.flush();
    Long id = monthlyBudgetRepository.findId(YEAR, MONTH).orElseThrow();
    return stateLogRepository.findAllByMonthlyBudgetIdOrderByIdAsc(id);
  }

  private LocalDateTime stopScheduledAt() {
    entityManager.flush();
    entityManager.clear();
    CashbackMonthlyBudget budget =
        monthlyBudgetRepository.findByBudgetYearAndBudgetMonth(YEAR, MONTH).orElseThrow();
    return budget.getStopScheduledAt();
  }

  private void setStatus(String status) {
    entityManager.flush();
    jdbcTemplate.update(
        "update cashback_monthly_budget set status = ? where budget_year = ? and budget_month = ?",
        status,
        YEAR,
        MONTH);
    entityManager.clear();
  }

  private void assertStopped(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.BUDGET_ALREADY_STOPPED);
  }

  // 목적: 공지 후 중단이 정상 달을 공지 상태로 바꾸고 중단 예약 시각(오늘 + 3일 00:00)과 이력(수동 사유, 처리자 없음, 당시 남은 비율)을 남기며 적립은
  // 계속 받는지 확인한다.
  @Test
  @DisplayName("공지 후 중단은 공지 상태와 예약 시각과 수동 이력을 남기고 적립은 계속 받는다")
  void noticeStop() {
    stateService.noticeStopManually(YEAR, MONTH);

    assertThat(status()).isEqualTo(BudgetStatus.NOTICED);
    assertThat(stopScheduledAt()).isEqualTo(LocalDateTime.of(2026, 11, 21, 0, 0));
    List<CashbackBudgetStateLog> logs = logs();
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getFromStatus()).isEqualTo(BudgetStatus.ACTIVE);
    assertThat(logs.get(0).getToStatus()).isEqualTo(BudgetStatus.NOTICED);
    assertThat(logs.get(0).getReason()).isEqualTo(StateChangeReason.OPERATOR_NOTICE_STOP);
    assertThat(logs.get(0).getOperatorId()).isNull();
    assertThat(logs.get(0).getRemainingRate()).isEqualByComparingTo("1");
    assertThat(budgetService.reserveBudget(YEAR, MONTH, 1_000L)).isEqualTo(BudgetReserveResult.RESERVED);
  }

  // 목적: 이미 공지 상태인 달에 공지 후 중단을 다시 눌러도 예약 시각이 바뀌지 않고 이력도 늘지 않는지 확인한다(시계를 하루 옮겨도 그대로).
  @Test
  @DisplayName("이미 공지 상태면 예약 시각을 바꾸지 않고 이력도 남기지 않는다")
  void noticeStop_isIdempotent() {
    stateService.noticeStopManually(YEAR, MONTH);
    ((MutableClock) clock).advance(Duration.ofDays(1));

    stateService.noticeStopManually(YEAR, MONTH);

    assertThat(stopScheduledAt()).isEqualTo(LocalDateTime.of(2026, 11, 21, 0, 0));
    assertThat(logs()).hasSize(1);
  }

  // 목적: 수동 공지 후 예약 시각이 지나면 스케줄러 처리가 중단으로 바꾸는지(자동 전이와 같은 경로) 확인한다.
  @Test
  @DisplayName("수동 공지 뒤 예약 시각이 지나면 중단된다")
  void noticeStop_thenScheduledStop() {
    stateService.noticeStopManually(YEAR, MONTH);
    ((MutableClock) clock).advance(Duration.ofDays(3));

    assertThat(stateService.stopDueScheduled()).isEqualTo(1);

    assertThat(status()).isEqualTo(BudgetStatus.STOPPED);
    assertThat(logs().get(1).getReason()).isEqualTo(StateChangeReason.SCHEDULED_TIME);
  }

  // 목적: 긴급 중단이 정상 달을 즉시 중단하고(이후 확보는 NOT_ACTIVE) 수동 사유의 이력을 남기는지 확인한다.
  @Test
  @DisplayName("긴급 중단은 즉시 중단하고 이후 확보를 거절하며 수동 이력을 남긴다")
  void emergencyStop_fromActive() {
    stateService.emergencyStopManually(YEAR, MONTH);

    assertThat(status()).isEqualTo(BudgetStatus.STOPPED);
    assertThat(budgetService.reserveBudget(YEAR, MONTH, 1_000L)).isEqualTo(BudgetReserveResult.NOT_ACTIVE);
    List<CashbackBudgetStateLog> logs = logs();
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getFromStatus()).isEqualTo(BudgetStatus.ACTIVE);
    assertThat(logs.get(0).getToStatus()).isEqualTo(BudgetStatus.STOPPED);
    assertThat(logs.get(0).getReason()).isEqualTo(StateChangeReason.OPERATOR_EMERGENCY_STOP);
    assertThat(logs.get(0).getOperatorId()).isNull();
  }

  // 목적: 공지 상태에서도 긴급 중단이 되고 이력에 변경 전 상태(공지)가 남는지 확인한다.
  @Test
  @DisplayName("공지 상태에서도 긴급 중단되고 이력에 공지에서 왔음이 남는다")
  void emergencyStop_fromNoticed() {
    stateService.noticeStopManually(YEAR, MONTH);

    stateService.emergencyStopManually(YEAR, MONTH);

    assertThat(status()).isEqualTo(BudgetStatus.STOPPED);
    List<CashbackBudgetStateLog> logs = logs();
    assertThat(logs).hasSize(2);
    assertThat(logs.get(1).getFromStatus()).isEqualTo(BudgetStatus.NOTICED);
    assertThat(logs.get(1).getReason()).isEqualTo(StateChangeReason.OPERATOR_EMERGENCY_STOP);
  }

  // 목적: 이미 중단된 달에 긴급 중단을 반복하면 CSH-012 로 거절되고 이력이 중복되지 않는지 확인한다.
  @Test
  @DisplayName("긴급 중단을 반복하면 거절되고 이력은 한 번만 남는다")
  void emergencyStop_repeated() {
    stateService.emergencyStopManually(YEAR, MONTH);

    assertStopped(() -> stateService.emergencyStopManually(YEAR, MONTH));

    assertThat(logs()).hasSize(1);
  }

  // 목적: 중단·마감된 달에는 공지 후 중단도 긴급 중단도 거절되고 상태·이력이 바뀌지 않는지 확인한다.
  @Test
  @DisplayName("중단·마감된 달은 두 중단 모두 거절한다")
  void stoppedOrClosed_rejected() {
    setStatus("STOPPED");
    assertStopped(() -> stateService.noticeStopManually(YEAR, MONTH));
    assertStopped(() -> stateService.emergencyStopManually(YEAR, MONTH));

    setStatus("CLOSED");
    assertStopped(() -> stateService.noticeStopManually(YEAR, MONTH));
    assertStopped(() -> stateService.emergencyStopManually(YEAR, MONTH));

    assertThat(status()).isEqualTo(BudgetStatus.CLOSED);
    assertThat(logs()).isEmpty();
  }

  // 목적: 월 예산이 없는 달은 CSH-004, 월이 1~12 가 아니면 CSH-007 로 거절되는지 확인한다.
  @Test
  @DisplayName("월 예산이 없으면 CSH-004, 월이 올바르지 않으면 CSH-007")
  void missingOrInvalidMonth() {
    assertThatThrownBy(() -> stateService.noticeStopManually(YEAR, 3))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.BUDGET_NOT_FOUND);
    assertThatThrownBy(() -> stateService.emergencyStopManually(YEAR, 3))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.BUDGET_NOT_FOUND);
    assertThatThrownBy(() -> stateService.emergencyStopManually(YEAR, 13))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
  }
}
