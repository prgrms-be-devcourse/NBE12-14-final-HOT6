package com.back.nbe1214finalhot6.domain.wallet.dto;

import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import java.time.LocalDateTime;

/**
 * 모의계좌 거래 내역 한 줄.
 *
 * @param transactionId 거래 ID
 * @param type 거래 종류(가입 보너스, 충전, 환불)
 * @param amount 부호 있는 금액. 입금은 +, 차감은 -. 충전은 계좌에서 빠지므로 -, 환불은 계좌로 돌아오므로 +
 * @param balanceAfter 이 거래 직후의 계좌 잔액
 * @param refKey 거래의 원인이 된 충전 ID 또는 환불 ID. 가입 보너스는 null
 * @param createdAt 거래 시각
 */
public record AccountTransactionResponse(
    Long transactionId,
    AccountTransactionType type,
    long amount,
    long balanceAfter,
    Long refKey,
    LocalDateTime createdAt) {

  public static AccountTransactionResponse from(AccountTransaction transaction) {
    return new AccountTransactionResponse(
        transaction.getId(),
        transaction.getType(),
        transaction.getAmount(),
        transaction.getBalanceAfter(),
        transaction.getRefKey(),
        transaction.getCreatedAt());
  }
}
