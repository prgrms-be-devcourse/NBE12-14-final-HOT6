package com.back.nbe1214finalhot6.domain.wallet.entity;

/** 가맹점 지갑 원장의 거래 종류. */
public enum MerchantWalletTransactionType {
  // 월 정산 지급: 지갑 입금(+)
  SETTLEMENT_PAYOUT,
  // 계좌로 출금: 지갑 차감(-)
  PAYOUT_TO_ACCOUNT
}
