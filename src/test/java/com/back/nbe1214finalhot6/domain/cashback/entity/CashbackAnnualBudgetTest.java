package com.back.nbe1214finalhot6.domain.cashback.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class CashbackAnnualBudgetTest {

  // 목적: 정상 값으로 만들면 총액이 담기고 예약·지급 금액은 0으로 시작하는지 확인한다.
  @Test
  @DisplayName("정상 값이면 연 예산이 만들어지고 예약·지급은 0으로 시작한다")
  void create_valid() {
    CashbackAnnualBudget budget = CashbackAnnualBudget.create(2026, 1_000_000L);

    assertThat(budget.getBudgetYear()).isEqualTo(2026);
    assertThat(budget.getTotalAmount()).isEqualTo(1_000_000L);
    assertThat(budget.getReservedAmount()).isZero();
    assertThat(budget.getPaidAmount()).isZero();
    assertThat(budget.remainingAmount()).isEqualTo(1_000_000L);
  }

  // 목적: 총액이 0 이하이면 거부되는지 확인한다.
  @ParameterizedTest
  @ValueSource(longs = {0L, -1L})
  @DisplayName("총액이 0 이하이면 거부한다")
  void create_invalidTotal(long total) {
    assertThatThrownBy(() -> CashbackAnnualBudget.create(2026, total))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
  }

  // 목적: 연도가 4자리가 아니면 거부되는지 확인한다.
  @ParameterizedTest
  @ValueSource(ints = {0, 26, 999, 10000})
  @DisplayName("연도가 4자리가 아니면 거부한다")
  void create_invalidYear(int year) {
    assertThatThrownBy(() -> CashbackAnnualBudget.create(year, 1_000L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
  }

  // 목적: 총액을 바꾸면 새 값이 반영되는지 확인한다.
  @Test
  @DisplayName("총액을 바꿀 수 있다")
  void changeTotal_valid() {
    CashbackAnnualBudget budget = CashbackAnnualBudget.create(2026, 1_000_000L);

    budget.changeTotal(2_000_000L);

    assertThat(budget.getTotalAmount()).isEqualTo(2_000_000L);
  }

  // 목적: 0 이하로 바꾸려 하면 거부되고 기존 값이 유지되는지 확인한다.
  @Test
  @DisplayName("총액을 0 이하로 바꾸면 거부하고 기존 값을 유지한다")
  void changeTotal_invalid() {
    CashbackAnnualBudget budget = CashbackAnnualBudget.create(2026, 1_000_000L);

    assertThatThrownBy(() -> budget.changeTotal(0L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
    assertThat(budget.getTotalAmount()).isEqualTo(1_000_000L);
  }

  // 목적: 이미 확보·지급한 금액보다 작게 줄이는 것은 막고, 같은 금액까지는 허용하는지 확인한다.
  @Test
  @DisplayName("이미 확보·지급한 금액보다 작게 줄일 수 없다")
  void changeTotal_belowUsed() {
    CashbackAnnualBudget budget = CashbackAnnualBudget.create(2026, 1_000_000L);
    ReflectionTestUtils.setField(budget, "reservedAmount", 300_000L);
    ReflectionTestUtils.setField(budget, "paidAmount", 200_000L);

    assertThatThrownBy(() -> budget.changeTotal(499_999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);

    budget.changeTotal(500_000L);
    assertThat(budget.getTotalAmount()).isEqualTo(500_000L);
    assertThat(budget.remainingAmount()).isZero();
  }
}
