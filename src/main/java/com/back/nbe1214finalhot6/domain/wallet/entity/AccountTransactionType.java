package com.back.nbe1214finalhot6.domain.wallet.entity;

/** 모의계좌 원장의 거래 종류. */
public enum AccountTransactionType {
  // 가입 시 지급하는 보너스(모의계좌 초기 잔액)
  SIGNUP_BONUS,
  // 충전: 모의계좌 → 지갑(계좌 잔액 감소)
  CHARGE,
  // 환불: 지갑 → 모의계좌(계좌 잔액 증가)
  REFUND
}
