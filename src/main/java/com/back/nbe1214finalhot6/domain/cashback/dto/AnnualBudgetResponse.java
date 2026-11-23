package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackAnnualBudget;

/**
 * 연 예산.
 *
 * @param carriedInAmount 다른 해에서 넘어온 금액(연말 이월)
 * @param carriedOutAmount 다른 해로 넘긴 금액(연말 이월)
 * @param remainingAmount 아직 확보하지 않은 남은 예산(총액 + 이월 유입 - 이월 유출 - 예약 - 지급)
 */
public record AnnualBudgetResponse(
    int budgetYear,
    long totalAmount,
    long carriedInAmount,
    long carriedOutAmount,
    long reservedAmount,
    long paidAmount,
    long remainingAmount) {

  public static AnnualBudgetResponse from(CashbackAnnualBudget budget) {
    return new AnnualBudgetResponse(
        budget.getBudgetYear(),
        budget.getTotalAmount(),
        budget.getCarriedInAmount(),
        budget.getCarriedOutAmount(),
        budget.getReservedAmount(),
        budget.getPaidAmount(),
        budget.remainingAmount());
  }
}
