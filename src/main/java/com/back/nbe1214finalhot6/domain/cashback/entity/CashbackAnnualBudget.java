package com.back.nbe1214finalhot6.domain.cashback.entity;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 캐시백 연 예산. 연도당 한 건이다.
 *
 * <p>금액은 원 단위 정수다. 예약·지급 금액은 0으로 시작하며, 결제와 지급 처리에서 바뀐다.
 */
@Entity
@Table(
    name = "cashback_annual_budget",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_cashback_annual_budget_year", columnNames = "budget_year"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashbackAnnualBudget {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "budget_year", nullable = false)
  private int budgetYear;

  @Column(name = "total_amount", nullable = false)
  private long totalAmount;

  // 결제 시 확보(예약)해 둔 금액. 아직 지급 전
  @Column(name = "reserved_amount", nullable = false)
  private long reservedAmount;

  // 지급이 끝난 금액
  @Column(name = "paid_amount", nullable = false)
  private long paidAmount;

  private CashbackAnnualBudget(int budgetYear, long totalAmount) {
    this.budgetYear = budgetYear;
    this.totalAmount = totalAmount;
  }

  /**
   * 연 예산을 만든다.
   *
   * @throws BusinessException 연도가 4자리가 아니거나 총액이 0 이하일 때
   */
  public static CashbackAnnualBudget create(int budgetYear, long totalAmount) {
    if (budgetYear < 1000 || budgetYear > 9999) {
      throw new BusinessException(CashbackErrorCode.INVALID_BUDGET, "연도는 4자리여야 합니다");
    }
    validateTotal(totalAmount);
    return new CashbackAnnualBudget(budgetYear, totalAmount);
  }

  /**
   * 총액을 바꾼다.
   *
   * @param allocatedSum 이미 월별로 배정한 금액의 합
   * @throws BusinessException 총액이 0 이하이거나, 이미 확보·지급한 금액보다 작을 때(CSH-007), 월 배정 합계보다 작을 때(CSH-005)
   */
  public void changeTotal(long totalAmount, long allocatedSum) {
    validateTotal(totalAmount);
    if (totalAmount < reservedAmount + paidAmount) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_BUDGET, "이미 확보하거나 지급한 금액보다 작게 줄일 수 없습니다");
    }
    if (totalAmount < allocatedSum) {
      throw new BusinessException(CashbackErrorCode.BUDGET_ALLOCATION_EXCEEDED);
    }
    this.totalAmount = totalAmount;
  }

  /** 아직 확보하지 않은 남은 예산. */
  public long remainingAmount() {
    return totalAmount - reservedAmount - paidAmount;
  }

  private static void validateTotal(long totalAmount) {
    if (totalAmount <= 0) {
      throw new BusinessException(CashbackErrorCode.INVALID_BUDGET, "총액은 0보다 커야 합니다");
    }
  }
}
