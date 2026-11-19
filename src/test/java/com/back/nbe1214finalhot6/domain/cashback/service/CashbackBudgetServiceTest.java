package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class CashbackBudgetServiceTest {

  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;

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
}
