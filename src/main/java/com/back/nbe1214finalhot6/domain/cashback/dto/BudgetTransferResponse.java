package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetTransferType;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackBudgetTransfer;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import java.time.LocalDateTime;

/**
 * 월 예산 이월 이력 한 건.
 *
 * @param transferId 이월 이력 ID
 * @param fromYear 내보낸 달의 연도
 * @param fromMonth 내보낸 달
 * @param toYear 받은 달의 연도
 * @param toMonth 받은 달
 * @param amount 이월한 금액(원)
 * @param type 이월 종류
 * @param createdBy 처리자. 인증이 머지되기 전까지 null 이다
 * @param createdAt 이월 시각
 */
public record BudgetTransferResponse(
    Long transferId,
    int fromYear,
    int fromMonth,
    int toYear,
    int toMonth,
    long amount,
    BudgetTransferType type,
    Long createdBy,
    LocalDateTime createdAt) {

  public static BudgetTransferResponse of(
      CashbackBudgetTransfer transfer, CashbackMonthlyBudget from, CashbackMonthlyBudget to) {
    return new BudgetTransferResponse(
        transfer.getId(),
        from.getBudgetYear(),
        from.getBudgetMonth(),
        to.getBudgetYear(),
        to.getBudgetMonth(),
        transfer.getAmount(),
        transfer.getType(),
        transfer.getCreatedBy(),
        transfer.getCreatedAt());
  }
}
