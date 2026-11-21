package com.back.nbe1214finalhot6.domain.wallet.entity;

/** 모의계좌 원장의 거래 종류. 환불은 W4에서 추가함. */
public enum AccountTransactionType {
  // 가입 시 지급하는 보너스(모의계좌 초기 잔액)
  SIGNUP_BONUS,
  // 충전: 모의계좌 → 지갑(계좌 잔액 감소)
  CHARGE
}
