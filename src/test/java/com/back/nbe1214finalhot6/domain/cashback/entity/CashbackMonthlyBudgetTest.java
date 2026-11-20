package com.back.nbe1214finalhot6.domain.cashback.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class CashbackMonthlyBudgetTest {

  private static final BigDecimal RATE = new BigDecimal("0.10");
  private static final BigDecimal BASE = new BigDecimal("0.03");

  private CashbackMonthlyBudget budget() {
    return CashbackMonthlyBudget.create(2026, 12, 1_000_000L, RATE, BASE);
  }

  // 목적: 정상 값이면 배정이 담기고, 대비금은 배정의 10%, 이월·예약·지급은 0, 상태는 ACTIVE 인지 확인한다.
  @Test
  @DisplayName("정상 값이면 월 예산이 만들어지고 대비금이 확정된다")
  void create_valid() {
    CashbackMonthlyBudget budget = budget();

    assertThat(budget.getBudgetYear()).isEqualTo(2026);
    assertThat(budget.getBudgetMonth()).isEqualTo(12);
    assertThat(budget.getAllocatedAmount()).isEqualTo(1_000_000L);
    assertThat(budget.getContingencyAmount()).isEqualTo(100_000L);
    assertThat(budget.getCarriedInAmount()).isZero();
    assertThat(budget.getReservedAmount()).isZero();
    assertThat(budget.getPaidAmount()).isZero();
    assertThat(budget.getStatus()).isEqualTo(BudgetStatus.ACTIVE);
    assertThat(budget.remainingAmount()).isEqualTo(1_000_000L);
  }

  // 목적: 대비금이 소수점 이하를 버리는 내림으로 계산되는지 확인한다(반올림이면 3이 된다).
  @Test
  @DisplayName("대비금은 내림으로 계산한다")
  void contingency_roundsDown() {
    assertThat(CashbackMonthlyBudget.create(2026, 12, 29L, RATE, BASE).getContingencyAmount())
        .isEqualTo(2L);
    assertThat(CashbackMonthlyBudget.create(2026, 12, 9L, RATE, BASE).getContingencyAmount()).isZero();
  }

  // 목적: 월이 1~12 를 벗어나면 거부되는지 확인한다.
  @ParameterizedTest
  @ValueSource(ints = {0, 13, -1})
  @DisplayName("월이 1~12 가 아니면 거부한다")
  void create_invalidMonth(int month) {
    assertThatThrownBy(() -> CashbackMonthlyBudget.create(2026, month, 1_000L, RATE, BASE))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
  }

  // 목적: 연도가 4자리가 아니거나 배정이 0 이하이면 거부되는지 확인한다.
  @Test
  @DisplayName("연도가 4자리가 아니거나 배정이 0 이하이면 거부한다")
  void create_invalidYearOrAmount() {
    assertThatThrownBy(() -> CashbackMonthlyBudget.create(26, 12, 1_000L, RATE, BASE))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
    assertThatThrownBy(() -> CashbackMonthlyBudget.create(2026, 12, 0L, RATE, BASE))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
  }

  // 목적: 사용 전에는 배정을 바꿀 수 있고 대비금도 새 배정 기준으로 다시 확정되는지 확인한다.
  @Test
  @DisplayName("사용 전에는 배정을 바꾸고 대비금을 다시 확정한다")
  void changeAllocated_beforeUse() {
    CashbackMonthlyBudget budget = budget();

    budget.changeAllocated(2_000_000L, RATE, null);

    assertThat(budget.getAllocatedAmount()).isEqualTo(2_000_000L);
    assertThat(budget.getContingencyAmount()).isEqualTo(200_000L);
  }

  // 목적: 예약이나 지급이 시작되면 배정 수정이 거부되고 값이 그대로인지 확인한다.
  @ParameterizedTest
  @ValueSource(strings = {"reservedAmount", "paidAmount"})
  @DisplayName("예약·지급이 시작되면 배정을 바꿀 수 없다")
  void changeAllocated_inUse(String field) {
    CashbackMonthlyBudget budget = budget();
    ReflectionTestUtils.setField(budget, field, 1L);

    assertThatThrownBy(() -> budget.changeAllocated(2_000_000L, RATE, null))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.BUDGET_IN_USE);
    assertThat(budget.getAllocatedAmount()).isEqualTo(1_000_000L);
    assertThat(budget.getContingencyAmount()).isEqualTo(100_000L);
  }

  // 목적: 수정 시 배정이 0 이하이면 거부되는지 확인한다.
  @Test
  @DisplayName("수정할 때 배정이 0 이하이면 거부한다")
  void changeAllocated_invalid() {
    CashbackMonthlyBudget budget = budget();

    assertThatThrownBy(() -> budget.changeAllocated(0L, RATE, null))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
  }

  // 목적: 기본 요율이 소수 넷째 자리로 저장되고, 잘못된 요율은 생성·수정에서 CSH-003 으로 거부되는지 확인한다.
  @Test
  @DisplayName("기본 요율은 정규화해 저장하고 잘못된 값은 거부한다")
  void baseRate_validation() {
    assertThat(budget().getBaseRate().toPlainString()).isEqualTo("0.0300");

    for (String bad : new String[] {"0", "1.01", "0.03001"}) {
      assertThatThrownBy(
              () -> CashbackMonthlyBudget.create(2026, 12, 1_000L, RATE, new BigDecimal(bad)))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_POLICY);
    }
    assertThatThrownBy(() -> CashbackMonthlyBudget.create(2026, 12, 1_000L, RATE, null))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_POLICY);
  }

  // 목적: 수정 시 기본 요율을 주면 바뀌고, 비우면 유지되며, 잘못된 값이면 배정도 바뀌지 않는지 확인한다.
  @Test
  @DisplayName("수정할 때 기본 요율을 주면 바꾸고 비우면 유지한다")
  void changeAllocated_baseRate() {
    CashbackMonthlyBudget budget = budget();

    budget.changeAllocated(2_000_000L, RATE, new BigDecimal("0.02"));
    assertThat(budget.getBaseRate().toPlainString()).isEqualTo("0.0200");

    budget.changeAllocated(3_000_000L, RATE, null);
    assertThat(budget.getBaseRate().toPlainString()).isEqualTo("0.0200");

    assertThatThrownBy(() -> budget.changeAllocated(4_000_000L, RATE, new BigDecimal("2")))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_POLICY);
    assertThat(budget.getAllocatedAmount()).isEqualTo(3_000_000L);
  }

  // 목적: 남은 금액이 배정 + 이월 유입 - 예약 - 지급 으로 계산되는지 확인한다.
  @Test
  @DisplayName("남은 금액은 배정 + 이월 유입 - 예약 - 지급이다")
  void remainingAmount() {
    CashbackMonthlyBudget budget = budget();
    ReflectionTestUtils.setField(budget, "carriedInAmount", 50_000L);
    ReflectionTestUtils.setField(budget, "reservedAmount", 200_000L);
    ReflectionTestUtils.setField(budget, "paidAmount", 100_000L);

    assertThat(budget.remainingAmount()).isEqualTo(750_000L);
  }
}
