package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/** 예산 확보·복원. 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class CashbackBudgetReservationTest {

  private static final int YEAR = 2026;
  private static final int MONTH = 12;

  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  @PersistenceContext private EntityManager entityManager;

  // 연 예산 12,000,000 / 12월 배정 1,000,000
  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(YEAR, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(
        YEAR, MONTH, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  // 확보는 영속성 컨텍스트를 비우지 않으므로, setUp 에서 읽어 둔 엔티티가 낡지 않도록 비우고 다시 읽는다
  private void reload() {
    entityManager.flush();
    entityManager.clear();
  }

  private long annualReserved() {
    reload();
    return budgetService.getAnnual(YEAR).reservedAmount();
  }

  private long monthlyReserved() {
    reload();
    return budgetService.getMonthly(YEAR, MONTH).reservedAmount();
  }

  private void assertInvalidBudget(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
  }

  // 목적: 확보하면 연 예산과 월 예산의 확보 금액이 함께 늘고 남은 예산이 줄어드는지 확인한다.
  @Test
  @DisplayName("확보하면 연·월 예산의 확보 금액이 함께 늘어난다")
  void reserve_updatesBoth() {
    BudgetReserveResult result = budgetService.reserveBudget(YEAR, MONTH, 300_000L);

    assertThat(result).isEqualTo(BudgetReserveResult.RESERVED);
    assertThat(annualReserved()).isEqualTo(300_000L);
    assertThat(monthlyReserved()).isEqualTo(300_000L);
    assertThat(budgetService.getMonthly(YEAR, MONTH).remainingAmount()).isEqualTo(700_000L);
    assertThat(budgetService.getAnnual(YEAR).remainingAmount()).isEqualTo(11_700_000L);
  }

  // 목적: 확보가 영속성 컨텍스트를 비우지 않아서, 호출한 쪽(결제 트랜잭션)이 먼저 읽어 둔 엔티티가 분리되지 않는지 확인한다.
  // 분리되면 이후 그 엔티티에 한 변경(결제 완료 등)이 저장되지 않는다.
  @Test
  @DisplayName("확보해도 호출한 쪽이 읽어 둔 엔티티는 영속 상태로 남는다")
  void reserve_keepsPersistenceContext() {
    CashbackMonthlyBudget loaded =
        monthlyBudgetRepository.findByBudgetYearAndBudgetMonth(YEAR, MONTH).orElseThrow();

    budgetService.reserveBudget(YEAR, MONTH, 100_000L);
    budgetService.releaseBudget(YEAR, MONTH, 100_000L);

    assertThat(entityManager.contains(loaded)).isTrue();
  }

  // 목적: 남은 만큼 정확히 확보되고(경계), 1원이라도 모자라면 전액 실패이며 아무것도 바뀌지 않는지 확인한다.
  @Test
  @DisplayName("남은 만큼은 확보하고 1원 모자라면 실패하며 값은 그대로다")
  void reserve_exactRemainingAndOneOver() {
    budgetService.reserveBudget(YEAR, MONTH, 400_000L);

    assertThat(budgetService.reserveBudget(YEAR, MONTH, 600_001L))
        .isEqualTo(BudgetReserveResult.INSUFFICIENT);
    assertThat(annualReserved()).isEqualTo(400_000L);
    assertThat(monthlyReserved()).isEqualTo(400_000L);

    assertThat(budgetService.reserveBudget(YEAR, MONTH, 600_000L))
        .isEqualTo(BudgetReserveResult.RESERVED);
    assertThat(monthlyReserved()).isEqualTo(1_000_000L);
    assertThat(budgetService.reserveBudget(YEAR, MONTH, 1L))
        .isEqualTo(BudgetReserveResult.INSUFFICIENT);
  }

  // 목적: 연 예산은 넉넉하지만 월 예산이 모자라면 실패하고, 먼저 늘렸던 연 예산 확보가 되돌려지는지 확인한다.
  @Test
  @DisplayName("월 예산이 모자라면 먼저 늘린 연 예산 확보를 되돌린다")
  void reserve_monthlyFailureRestoresAnnual() {
    assertThat(budgetService.reserveBudget(YEAR, MONTH, 1_000_001L))
        .isEqualTo(BudgetReserveResult.INSUFFICIENT);

    assertThat(annualReserved()).isZero();
    assertThat(monthlyReserved()).isZero();
  }

  // 목적: 이월 유입과 지급 완료 금액이 남은 예산 계산에 반영되는지 확인한다.
  @Test
  @DisplayName("남은 예산은 이월 유입을 더하고 지급 금액을 뺀다")
  void reserve_countsCarriedInAndPaid() {
    CashbackMonthlyBudget stored =
        monthlyBudgetRepository.findByBudgetYearAndBudgetMonth(YEAR, MONTH).orElseThrow();
    ReflectionTestUtils.setField(stored, "carriedInAmount", 500_000L);
    ReflectionTestUtils.setField(stored, "paidAmount", 200_000L);

    // 남은 예산 = 1,000,000 + 500,000 - 0 - 200,000 = 1,300,000
    assertThat(budgetService.reserveBudget(YEAR, MONTH, 1_300_001L))
        .isEqualTo(BudgetReserveResult.INSUFFICIENT);
    assertThat(budgetService.reserveBudget(YEAR, MONTH, 1_300_000L))
        .isEqualTo(BudgetReserveResult.RESERVED);
  }

  // 목적: 적립을 받는 상태(ACTIVE, NOTICED)는 확보되고 중단·마감 상태는 거절되는지 확인한다.
  @ParameterizedTest
  @EnumSource(BudgetStatus.class)
  @DisplayName("적립 중단·마감 달에는 확보하지 않는다")
  void reserve_byStatus(BudgetStatus status) {
    CashbackMonthlyBudget stored =
        monthlyBudgetRepository.findByBudgetYearAndBudgetMonth(YEAR, MONTH).orElseThrow();
    ReflectionTestUtils.setField(stored, "status", status);

    BudgetReserveResult result = budgetService.reserveBudget(YEAR, MONTH, 100_000L);

    boolean accepting = status == BudgetStatus.ACTIVE || status == BudgetStatus.NOTICED;
    assertThat(result)
        .isEqualTo(accepting ? BudgetReserveResult.RESERVED : BudgetReserveResult.NOT_ACTIVE);
    assertThat(annualReserved()).isEqualTo(accepting ? 100_000L : 0L);
    assertThat(monthlyReserved()).isEqualTo(accepting ? 100_000L : 0L);
  }

  // 목적: 연 예산이나 월 예산이 없으면 NO_BUDGET 이고 아무것도 바뀌지 않는지 확인한다.
  @Test
  @DisplayName("연 예산이나 월 예산이 없으면 NO_BUDGET 이다")
  void reserve_noBudget() {
    assertThat(budgetService.reserveBudget(2030, MONTH, 1_000L))
        .isEqualTo(BudgetReserveResult.NO_BUDGET);
    assertThat(budgetService.reserveBudget(YEAR, 11, 1_000L))
        .isEqualTo(BudgetReserveResult.NO_BUDGET);

    assertThat(annualReserved()).isZero();
  }

  // 목적: 금액이 0 이하이거나 월이 범위를 벗어나면 거부되고 값이 바뀌지 않는지 확인한다.
  @Test
  @DisplayName("금액이 0 이하이거나 월이 잘못되면 CSH-007 로 거부한다")
  void reserveAndRelease_invalidInput() {
    assertInvalidBudget(() -> budgetService.reserveBudget(YEAR, MONTH, 0L));
    assertInvalidBudget(() -> budgetService.reserveBudget(YEAR, MONTH, -1L));
    assertInvalidBudget(() -> budgetService.reserveBudget(YEAR, 13, 1_000L));
    assertInvalidBudget(() -> budgetService.releaseBudget(YEAR, MONTH, 0L));
    assertInvalidBudget(() -> budgetService.releaseBudget(YEAR, 0, 1_000L));

    assertThat(annualReserved()).isZero();
  }

  // 목적: 복원하면 연·월 예산이 함께 줄고, 복원한 뒤 다시 확보할 수 있는지 확인한다.
  @Test
  @DisplayName("복원하면 연·월 예산이 함께 줄고 다시 확보할 수 있다")
  void release_restoresBoth() {
    budgetService.reserveBudget(YEAR, MONTH, 1_000_000L);
    assertThat(budgetService.reserveBudget(YEAR, MONTH, 1L))
        .isEqualTo(BudgetReserveResult.INSUFFICIENT);

    budgetService.releaseBudget(YEAR, MONTH, 400_000L);

    assertThat(annualReserved()).isEqualTo(600_000L);
    assertThat(monthlyReserved()).isEqualTo(600_000L);
    assertThat(budgetService.reserveBudget(YEAR, MONTH, 400_000L))
        .isEqualTo(BudgetReserveResult.RESERVED);
  }

  // 목적: 확보한 금액보다 많이 복원하려 하면 거부되는지 확인한다. 롤백 확인은 동시성 테스트 쪽(실제 커밋·롤백)에서 한다.
  @Test
  @DisplayName("확보한 금액보다 많이 복원하면 CSH-007 로 거부한다")
  void release_overRelease() {
    budgetService.reserveBudget(YEAR, MONTH, 100_000L);

    assertInvalidBudget(() -> budgetService.releaseBudget(YEAR, MONTH, 100_001L));
  }
}
