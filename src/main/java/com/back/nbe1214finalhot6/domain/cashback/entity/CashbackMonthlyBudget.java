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

  // 다음 달로 넘긴 금액. 이월 처리에서만 바뀐다. 기존 행은 0 으로 채운다
  @Column(name = "carried_out_amount", nullable = false, columnDefinition = "bigint not null default 0")
  private long carriedOutAmount;

  // 결제 시 확보(예약)해 둔 금액. 아직 지급 전
  @Column(name = "reserved_amount", nullable = false)
  private long reservedAmount;

  // 지급이 끝난 금액
  @Column(name = "paid_amount", nullable = false)
  private long paidAmount;

  // 월 시작 시 확정하는 대비금. 예약(reserved)과 구분한다
  @Column(name = "contingency_amount", nullable = false)
  private long contingencyAmount;

  // 그 달의 기본 요율(소수 비율). 달 중 인하 구간이 없을 때 적용한다. 이전에 만든 행은 비어 있을 수 있다
  @Column(name = "base_rate", precision = 5, scale = CashbackPolicy.RATE_SCALE)
  private BigDecimal baseRate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private BudgetStatus status;

  private CashbackMonthlyBudget(
      int budgetYear,
      int budgetMonth,
      long allocatedAmount,
      long contingencyAmount,
      BigDecimal baseRate) {
    this.budgetYear = budgetYear;
    this.budgetMonth = budgetMonth;
    this.allocatedAmount = allocatedAmount;
    this.contingencyAmount = contingencyAmount;
    this.baseRate = baseRate;
    this.status = BudgetStatus.ACTIVE;
  }

  /**
   * 월 예산을 만든다.
   *
   * @param contingencyRate 대비금 비율. 배정 × 비율을 내림해 확정한다
   * @param baseRate 그 달의 기본 요율
   * @throws BusinessException 연이 4자리가 아니거나 월이 1~12가 아니거나 배정이 0 이하이거나 기본 요율이 올바르지 않을 때
   */
  public static CashbackMonthlyBudget create(
      int budgetYear,
      int budgetMonth,
      long allocatedAmount,
      BigDecimal contingencyRate,
      BigDecimal baseRate) {
    if (budgetYear < 1000 || budgetYear > 9999) {
      throw new BusinessException(CashbackErrorCode.INVALID_BUDGET, "연도는 4자리여야 합니다");
    }
    validateMonth(budgetMonth);
    validateAllocated(allocatedAmount);
    CashbackPolicy.validateRate(baseRate);
    return new CashbackMonthlyBudget(
        budgetYear,
        budgetMonth,
        allocatedAmount,
        contingency(allocatedAmount, contingencyRate),
        baseRate.setScale(CashbackPolicy.RATE_SCALE));
  }

  /**
   * 배정을 바꾸고 대비금을 다시 확정한다.
   *
   * @param baseRate 새 기본 요율. null 이면 기존 값을 유지한다
   * @throws BusinessException 배정이나 기본 요율이 올바르지 않을 때, 예약·지급이 이미 시작되었을 때
   */
  public void changeAllocated(
      long allocatedAmount, BigDecimal contingencyRate, BigDecimal baseRate) {
    validateAllocated(allocatedAmount);
    if (baseRate != null) {
      CashbackPolicy.validateRate(baseRate);
    }
    if (reservedAmount + paidAmount + carriedOutAmount > 0) {
      throw new BusinessException(CashbackErrorCode.BUDGET_IN_USE);
    }
    this.allocatedAmount = allocatedAmount;
    this.contingencyAmount = contingency(allocatedAmount, contingencyRate);
    if (baseRate != null) {
      this.baseRate = baseRate.setScale(CashbackPolicy.RATE_SCALE);
    }
  }

  /** 아직 확보하지 않은 남은 예산(배정 + 이월 유입 - 이월 유출 - 예약 - 지급). */
  public long remainingAmount() {
    return allocatedAmount + carriedInAmount - carriedOutAmount - reservedAmount - paidAmount;
  }

  /** 비율의 분모. 그 달에 쓸 수 있는 전체 예산(배정 + 이월 유입). */
  public long totalBudget() {
    return allocatedAmount + carriedInAmount;
  }

  /** 소진율 = (확보 + 지급) ÷ (배정 + 이월 유입). 전체 예산이 0 이하면 0 이다. 소수 비율이고 소수 넷째 자리까지. */
  public BigDecimal usedRate() {
    return ratio(reservedAmount + paidAmount);
  }

  /** 남은 예산 비율 = 남은 예산 ÷ (배정 + 이월 유입). 전체 예산이 0 이하면 0 이다. 소수 비율이고 소수 넷째 자리까지. */
  public BigDecimal remainingRate() {
    return ratio(remainingAmount());
  }

  /**
   * 대비금 경고 단계. 남은 예산이 대비금 × 긴급선 비율 이하면 긴급선, 대비금 이하면 공지선이고 그 위는 경고 없음이다. 기준선과 같은 금액은 해당 단계로
   * 본다. 저장된 값이 아니라 현재 금액으로 계산한다.
   *
   * @param emergencyRatio 긴급선 비율(예: 0.5 = 대비금의 절반)
   */
  public BudgetWarning warning(BigDecimal emergencyRatio) {
    long remaining = remainingAmount();
    if (BigDecimal.valueOf(remaining)
            .compareTo(BigDecimal.valueOf(contingencyAmount).multiply(emergencyRatio))
        <= 0) {
      return BudgetWarning.EMERGENCY_LINE;
    }
    if (remaining <= contingencyAmount) {
      return BudgetWarning.NOTICE_LINE;
    }
    return BudgetWarning.NONE;
  }

  private BigDecimal ratio(long amount) {
    long total = totalBudget();
    if (total <= 0) {
      return BigDecimal.ZERO.setScale(CashbackPolicy.RATE_SCALE);
    }
    return BigDecimal.valueOf(amount)
        .divide(BigDecimal.valueOf(total), CashbackPolicy.RATE_SCALE, RoundingMode.HALF_UP);
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
