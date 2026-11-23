package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetWarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import java.math.BigDecimal;

/**
 * 월 예산.
 *
 * @param baseRate 그 달의 기본 요율. 이전에 만든 달은 null 일 수 있다
 * @param contingencyAmount 월 시작 시 확정한 대비금
 * @param carriedOutAmount 다음 달로 넘긴 금액
 * @param remainingAmount 아직 확보하지 않은 남은 예산(배정 + 이월 유입 - 이월 유출 - 예약 - 지급)
 * @param usedRate 소진율 = (확보 + 지급) ÷ (배정 + 이월 유입 - 이월 유출). 소수 비율(0.25 = 25%), 전체 예산이 0 이하면 0
 * @param remainingRate 남은 예산 비율 = 남은 예산 ÷ (배정 + 이월 유입 - 이월 유출). 전체 예산이 0 이하면 0
 * @param warning 대비금 경고. 남은 예산이 대비금 이하면 {@code NOTICE_LINE}, 대비금 × 긴급선 비율 이하면 {@code EMERGENCY_LINE}
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
    BigDecimal usedRate,
    BigDecimal remainingRate,
    BudgetWarning warning,
    BigDecimal baseRate,
    BudgetStatus status) {

  /** @param emergencyRatio 긴급선 비율(`cashback.emergency-ratio`) */
  public static MonthlyBudgetResponse from(CashbackMonthlyBudget budget, BigDecimal emergencyRatio) {
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
        budget.usedRate(),
        budget.remainingRate(),
        budget.warning(emergencyRatio),
        budget.getBaseRate(),
        budget.getStatus());
  }
}
