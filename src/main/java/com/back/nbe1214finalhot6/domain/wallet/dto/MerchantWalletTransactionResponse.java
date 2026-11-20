package com.back.nbe1214finalhot6.domain.wallet.dto;

import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransactionType;

/** 입금·출금 결과. 이미 반영된 건을 다시 요청하면 처음 반영된 거래를 그대로 돌려줌. */
public record MerchantWalletTransactionResponse(
    MerchantWalletTransactionType type, long amount, long balanceAfter, Long refKey) {

  public static MerchantWalletTransactionResponse from(MerchantWalletTransaction transaction) {
    return new MerchantWalletTransactionResponse(
        transaction.getType(),
        transaction.getAmount(),
        transaction.getBalanceAfter(),
        transaction.getRefKey());
  }
}
