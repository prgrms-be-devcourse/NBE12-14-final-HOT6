package com.back.nbe1214finalhot6.domain.cashback.entity;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 캐시백 월 예산.
 *
 * <p>연 예산을 월별로 배정한 금액이다. 대비금은 배정 시점에 확정하고, 결제가 시작되면 배정을 바꿀 수 없다. 금액은 원 단위 정수다.
 */
@Entity
@Table(
    name = "cashback_monthly_budget",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_cashback_monthly_budget_year_month",
            columnNames = {"budget_year", "budget_month"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashbackMonthlyBudget {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "budget_year", nullable = false)
  private int budgetYear;

  @Column(name = "budget_month", nullable = false)
  private int budgetMonth;

  @Column(name = "allocated_amount", nullable = false)
  private long allocatedAmount;

  // 이전 달에서 넘어온 금액. 이월 처리에서만 바뀐다
  @Column(name = "carried_in_amount", nullable = false)
  private long carriedInAmount;

  // 결제 시 확보(예약)해 둔 금액. 아직 지급 전
  @Column(name = "reserved_amount", nullable = false)
  private long reservedAmount;

  // 지급이 끝난 금액
  @Column(name = "paid_amount", nullable = false)
  private long paidAmount;

  // 월 시작 시 확정하는 대비금. 예약(reserved)과 구분한다
  @Column(name = "contingency_amount", nullable = false)
  private long contingencyAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private BudgetStatus status;

  private CashbackMonthlyBudget(
      int budgetYear, int budgetMonth, long allocatedAmount, long contingencyAmount) {
    this.budgetYear = budgetYear;
    this.budgetMonth = budgetMonth;
    this.allocatedAmount = allocatedAmount;
    this.contingencyAmount = contingencyAmount;
    this.status = BudgetStatus.ACTIVE;
  }

  /**
   * 월 예산을 만든다.
   *
   * @param contingencyRate 대비금 비율. 배정 × 비율을 내림해 확정한다
   * @throws BusinessException 연이 4자리가 아니거나 월이 1~12가 아니거나 배정이 0 이하일 때
   */
  public static CashbackMonthlyBudget create(
      int budgetYear, int budgetMonth, long allocatedAmount, BigDecimal contingencyRate) {
    if (budgetYear < 1000 || budgetYear > 9999) {
      throw new BusinessException(CashbackErrorCode.INVALID_BUDGET, "연도는 4자리여야 합니다");
    }
    validateMonth(budgetMonth);
    validateAllocated(allocatedAmount);
    return new CashbackMonthlyBudget(
        budgetYear,
        budgetMonth,
        allocatedAmount,
        contingency(allocatedAmount, contingencyRate));
  }

  /**
   * 배정을 바꾸고 대비금을 다시 확정한다.
   *
   * @throws BusinessException 배정이 0 이하일 때, 예약·지급이 이미 시작되었을 때
   */
  public void changeAllocated(long allocatedAmount, BigDecimal contingencyRate) {
    validateAllocated(allocatedAmount);
    if (reservedAmount + paidAmount > 0) {
      throw new BusinessException(CashbackErrorCode.BUDGET_IN_USE);
    }
    this.allocatedAmount = allocatedAmount;
    this.contingencyAmount = contingency(allocatedAmount, contingencyRate);
  }

  /** 아직 확보하지 않은 남은 예산(배정 + 이월 유입 - 예약 - 지급). */
  public long remainingAmount() {
    return allocatedAmount + carriedInAmount - reservedAmount - paidAmount;
  }

  public static void validateMonth(int budgetMonth) {
    if (budgetMonth < 1 || budgetMonth > 12) {
      throw new BusinessException(CashbackErrorCode.INVALID_BUDGET, "월은 1~12 여야 합니다");
    }
  }

  private static void validateAllocated(long allocatedAmount) {
    if (allocatedAmount <= 0) {
      throw new BusinessException(CashbackErrorCode.INVALID_BUDGET, "배정은 0보다 커야 합니다");
    }
  }

  private static long contingency(long allocatedAmount, BigDecimal rate) {
    return BigDecimal.valueOf(allocatedAmount)
        .multiply(rate)
        .setScale(0, RoundingMode.DOWN)
        .longValue();
  }
}
