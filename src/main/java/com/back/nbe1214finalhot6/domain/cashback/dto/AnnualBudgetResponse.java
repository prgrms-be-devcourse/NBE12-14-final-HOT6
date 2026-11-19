package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackAnnualBudget;

/**
 * 연 예산.
 *
 * @param remainingAmount 아직 확보하지 않은 남은 예산(총액 - 예약 - 지급)
 */
public record AnnualBudgetResponse(
    int budgetYear,
    long totalAmount,
    long reservedAmount,
    long paidAmount,
    long remainingAmount) {

  public static AnnualBudgetResponse from(CashbackAnnualBudget budget) {
    return new AnnualBudgetResponse(
        budget.getBudgetYear(),
        budget.getTotalAmount(),
        budget.getReservedAmount(),
        budget.getPaidAmount(),
        budget.remainingAmount());
  }
}
