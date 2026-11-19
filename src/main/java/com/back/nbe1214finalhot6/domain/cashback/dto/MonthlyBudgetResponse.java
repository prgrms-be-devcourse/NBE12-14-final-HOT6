package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;

/**
 * 월 예산.
 *
 * @param contingencyAmount 월 시작 시 확정한 대비금
 * @param remainingAmount 아직 확보하지 않은 남은 예산(배정 + 이월 유입 - 예약 - 지급)
 */
public record MonthlyBudgetResponse(
    int budgetYear,
    int budgetMonth,
    long allocatedAmount,
    long carriedInAmount,
    long reservedAmount,
    long paidAmount,
    long contingencyAmount,
    long remainingAmount,
    BudgetStatus status) {

  public static MonthlyBudgetResponse from(CashbackMonthlyBudget budget) {
    return new MonthlyBudgetResponse(
        budget.getBudgetYear(),
        budget.getBudgetMonth(),
        budget.getAllocatedAmount(),
        budget.getCarriedInAmount(),
        budget.getReservedAmount(),
        budget.getPaidAmount(),
        budget.getContingencyAmount(),
        budget.remainingAmount(),
        budget.getStatus());
  }
}
