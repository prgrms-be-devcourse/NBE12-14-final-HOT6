package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import java.math.BigDecimal;

/**
 * 월 예산.
 *
 * @param baseRate 그 달의 기본 요율. 이전에 만든 달은 null 일 수 있다
 * @param contingencyAmount 월 시작 시 확정한 대비금
 * @param carriedOutAmount 다음 달로 넘긴 금액
 * @param remainingAmount 아직 확보하지 않은 남은 예산(배정 + 이월 유입 - 이월 유출 - 예약 - 지급)
 */
public record MonthlyBudgetResponse(
    int budgetYear,
    int budgetMonth,
    long allocatedAmount,
    long carriedInAmount,
    long carriedOutAmount,
    long reservedAmount,
    long paidAmount,
    long contingencyAmount,
    long remainingAmount,
    BigDecimal baseRate,
    BudgetStatus status) {

  public static MonthlyBudgetResponse from(CashbackMonthlyBudget budget) {
    return new MonthlyBudgetResponse(
        budget.getBudgetYear(),
        budget.getBudgetMonth(),
        budget.getAllocatedAmount(),
        budget.getCarriedInAmount(),
        budget.getCarriedOutAmount(),
        budget.getReservedAmount(),
        budget.getPaidAmount(),
        budget.getContingencyAmount(),
        budget.remainingAmount(),
        budget.getBaseRate(),
        budget.getStatus());
  }
}
