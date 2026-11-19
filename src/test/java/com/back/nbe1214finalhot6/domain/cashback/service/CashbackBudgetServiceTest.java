package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class CashbackBudgetServiceTest {

  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;

  // 목적: 없는 연도에 PUT 하면 새로 만들어지고 DB에 저장되는지 확인한다.
  @Test
  @DisplayName("없는 연도에 PUT 하면 연 예산이 새로 만들어진다")
  void putAnnual_creates() {
    AnnualBudgetResponse response = budgetService.putAnnual(2026, new AnnualBudgetRequest(1_000_000L));

    assertThat(response.budgetYear()).isEqualTo(2026);
    assertThat(response.totalAmount()).isEqualTo(1_000_000L);
    assertThat(response.reservedAmount()).isZero();
    assertThat(response.paidAmount()).isZero();
    assertThat(response.remainingAmount()).isEqualTo(1_000_000L);
    assertThat(annualBudgetRepository.findByBudgetYear(2026)).isPresent();
  }

  // 목적: 이미 있는 연도에 PUT 하면 새 행이 생기지 않고 총액만 바뀌는지 확인한다.
  @Test
  @DisplayName("같은 연도에 다시 PUT 하면 총액이 바뀌고 한 건만 남는다")
  void putAnnual_updatesSameRow() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(1_000_000L));

    AnnualBudgetResponse response = budgetService.putAnnual(2026, new AnnualBudgetRequest(2_000_000L));

    assertThat(response.totalAmount()).isEqualTo(2_000_000L);
    assertThat(annualBudgetRepository.count()).isEqualTo(1);
  }

  // 목적: 연도가 다르면 별개의 예산으로 저장되는지 확인한다.
  @Test
  @DisplayName("연도가 다르면 별개의 예산이다")
  void putAnnual_differentYears() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(1_000_000L));
    budgetService.putAnnual(2027, new AnnualBudgetRequest(3_000_000L));

    assertThat(budgetService.getAnnual(2026).totalAmount()).isEqualTo(1_000_000L);
    assertThat(budgetService.getAnnual(2027).totalAmount()).isEqualTo(3_000_000L);
  }

  // 목적: 총액이 0 이하이면 CSH-007 로 거부되고 저장되지 않는지 확인한다.
  @Test
  @DisplayName("총액이 0 이하이면 거부하고 저장하지 않는다")
  void putAnnual_invalidTotal() {
    assertThatThrownBy(() -> budgetService.putAnnual(2026, new AnnualBudgetRequest(0L)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);

    assertThat(annualBudgetRepository.count()).isZero();
  }

  // 목적: 수정 시 총액이 0 이하이면 거부되고 기존 총액이 유지되는지 확인한다.
  @Test
  @DisplayName("수정할 때 총액이 0 이하이면 거부하고 기존 값을 유지한다")
  void putAnnual_invalidTotalOnUpdate() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(1_000_000L));

    assertThatThrownBy(() -> budgetService.putAnnual(2026, new AnnualBudgetRequest(-5L)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);

    assertThat(budgetService.getAnnual(2026).totalAmount()).isEqualTo(1_000_000L);
  }

  // 목적: 없는 연도를 조회하면 예산 없음 에러가 나는지 확인한다.
  @Test
  @DisplayName("없는 연도를 조회하면 CSH-004")
  void getAnnual_notFound() {
    assertThatThrownBy(() -> budgetService.getAnnual(2030))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.BUDGET_NOT_FOUND);
  }

  // ---- 월 배정 (테스트 시계: 2026-11-18 09:00 KST) ----

  private MonthlyBudgetResponse putMonthly(int year, int month, long amount) {
    return budgetService.putMonthly(year, month, new MonthlyBudgetRequest(amount));
  }

  private void assertErrorOnMonthly(int year, int month, long amount, CashbackErrorCode code) {
    assertThatThrownBy(() -> putMonthly(year, month, amount))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", code);
  }

  // 목적: 월 배정이 저장되고 대비금(10%)·상태·남은 금액이 응답에 담기는지 확인한다.
  @Test
  @DisplayName("월 배정을 등록하면 대비금이 확정되어 저장된다")
  void putMonthly_creates() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));

    MonthlyBudgetResponse response = putMonthly(2026, 12, 1_000_000L);

    assertThat(response.budgetYear()).isEqualTo(2026);
    assertThat(response.budgetMonth()).isEqualTo(12);
    assertThat(response.allocatedAmount()).isEqualTo(1_000_000L);
    assertThat(response.contingencyAmount()).isEqualTo(100_000L);
    assertThat(response.remainingAmount()).isEqualTo(1_000_000L);
    assertThat(response.status()).isEqualTo(BudgetStatus.ACTIVE);
    assertThat(monthlyBudgetRepository.findByBudgetYearAndBudgetMonth(2026, 12)).isPresent();
  }

  // 목적: 같은 달에 다시 PUT 하면 새 행 없이 배정과 대비금이 바뀌는지 확인한다.
  @Test
  @DisplayName("같은 달에 다시 PUT 하면 배정과 대비금이 바뀌고 한 건만 남는다")
  void putMonthly_updatesSameRow() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    putMonthly(2026, 12, 1_000_000L);

    MonthlyBudgetResponse response = putMonthly(2026, 12, 2_000_000L);

    assertThat(response.allocatedAmount()).isEqualTo(2_000_000L);
    assertThat(response.contingencyAmount()).isEqualTo(200_000L);
    assertThat(monthlyBudgetRepository.count()).isEqualTo(1);
  }

  // 목적: 연 예산이 없는 연도에는 배정할 수 없는지 확인한다.
  @Test
  @DisplayName("연 예산이 없으면 CSH-004")
  void putMonthly_noAnnual() {
    assertErrorOnMonthly(2026, 12, 1_000L, CashbackErrorCode.BUDGET_NOT_FOUND);
  }

  // 목적: 월 배정 합계가 연 총액과 같으면 허용되고 1원이라도 넘으면 거부되는지 확인한다(경계).
  @Test
  @DisplayName("월 배정 합계는 연 예산 이하여야 하고 넘으면 CSH-005")
  void putMonthly_sumBoundary() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(3_000_000L));
    putMonthly(2026, 11, 1_000_000L);
    putMonthly(2026, 12, 1_000_000L);

    // 자기 달의 기존 배정은 합계에서 빼므로 합계가 딱 3,000,000 이 되는 수정은 허용된다
    assertThat(putMonthly(2026, 11, 2_000_000L).allocatedAmount()).isEqualTo(2_000_000L);
    assertErrorOnMonthly(2026, 11, 2_000_001L, CashbackErrorCode.BUDGET_ALLOCATION_EXCEEDED);
    assertThat(budgetService.getMonthly(2026, 11).allocatedAmount()).isEqualTo(2_000_000L);
    assertErrorOnMonthly(2026, 10, 1L, CashbackErrorCode.INVALID_BUDGET);
  }

  // 목적: 다른 연도의 배정은 합계에 섞이지 않는지 확인한다.
  @Test
  @DisplayName("다른 연도의 배정은 합계에 포함하지 않는다")
  void putMonthly_sumIsPerYear() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(1_000_000L));
    budgetService.putAnnual(2027, new AnnualBudgetRequest(1_000_000L));
    putMonthly(2026, 12, 1_000_000L);

    assertThat(putMonthly(2027, 1, 1_000_000L).allocatedAmount()).isEqualTo(1_000_000L);
  }

  // 목적: 지난 달은 거부되고, 현재 달과 미래 달은 허용되는지 확인한다.
  @Test
  @DisplayName("지난 달은 배정할 수 없고 현재 달은 가능하다")
  void putMonthly_pastMonth() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));

    assertErrorOnMonthly(2026, 10, 1_000L, CashbackErrorCode.INVALID_BUDGET);
    assertThat(putMonthly(2026, 11, 1_000L).budgetMonth()).isEqualTo(11);
    assertThat(monthlyBudgetRepository.count()).isEqualTo(1);
  }

  // 목적: 월이 범위를 벗어나거나 배정이 0 이하이면 CSH-007 로 거부되고 저장되지 않는지 확인한다.
  @Test
  @DisplayName("월 범위와 금액이 올바르지 않으면 거부하고 저장하지 않는다")
  void putMonthly_invalidInput() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));

    assertErrorOnMonthly(2026, 13, 1_000L, CashbackErrorCode.INVALID_BUDGET);
    assertErrorOnMonthly(2026, 0, 1_000L, CashbackErrorCode.INVALID_BUDGET);
    assertErrorOnMonthly(2026, 12, 0L, CashbackErrorCode.INVALID_BUDGET);
    assertThat(monthlyBudgetRepository.count()).isZero();
  }

  // 목적: 예약이 시작된 달의 배정 수정이 CSH-008 로 거부되고 값이 유지되는지 확인한다.
  @Test
  @DisplayName("예약이 시작된 달은 수정할 수 없다")
  void putMonthly_inUse() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    putMonthly(2026, 12, 1_000_000L);
    CashbackMonthlyBudget stored =
        monthlyBudgetRepository.findByBudgetYearAndBudgetMonth(2026, 12).orElseThrow();
    ReflectionTestUtils.setField(stored, "reservedAmount", 500L);

    assertErrorOnMonthly(2026, 12, 2_000_000L, CashbackErrorCode.BUDGET_IN_USE);

    assertThat(budgetService.getMonthly(2026, 12).allocatedAmount()).isEqualTo(1_000_000L);
  }

  // 목적: 연 총액을 월 배정 합계보다 작게 줄이면 거부되고, 합계와 같으면 허용되는지 확인한다.
  @Test
  @DisplayName("연 총액은 월 배정 합계보다 작게 줄일 수 없다")
  void putAnnual_belowAllocated() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(5_000_000L));
    putMonthly(2026, 11, 1_000_000L);
    putMonthly(2026, 12, 2_000_000L);

    assertThatThrownBy(() -> budgetService.putAnnual(2026, new AnnualBudgetRequest(2_999_999L)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.BUDGET_ALLOCATION_EXCEEDED);
    assertThat(budgetService.getAnnual(2026).totalAmount()).isEqualTo(5_000_000L);
    assertThat(budgetService.putAnnual(2026, new AnnualBudgetRequest(3_000_000L)).totalAmount())
        .isEqualTo(3_000_000L);
  }

  // 목적: 월 조회와 연도별 목록(월 순서, 배정한 달만, 다른 연도 제외)이 맞는지 확인한다.
  @Test
  @DisplayName("월 조회와 연도별 목록은 배정한 달만 월 순서로 돌려준다")
  void getAndList() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putAnnual(2027, new AnnualBudgetRequest(12_000_000L));
    putMonthly(2026, 12, 3_000_000L);
    putMonthly(2026, 11, 1_000_000L);
    putMonthly(2027, 1, 2_000_000L);

    assertThat(budgetService.getMonthly(2026, 12).allocatedAmount()).isEqualTo(3_000_000L);
    assertThat(budgetService.listMonthly(2026))
        .extracting(MonthlyBudgetResponse::budgetMonth)
        .containsExactly(11, 12);
    assertThat(budgetService.listMonthly(2030)).isEmpty();
  }

  // 목적: 배정하지 않은 달을 조회하면 예산 없음 에러가 나는지 확인한다.
  @Test
  @DisplayName("배정하지 않은 달을 조회하면 CSH-004")
  void getMonthly_notFound() {
    assertThatThrownBy(() -> budgetService.getMonthly(2026, 12))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.BUDGET_NOT_FOUND);
  }
}
