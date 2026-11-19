package com.back.nbe1214finalhot6.domain.wallet.entity;

/**
 * 지갑 원장의 거래 종류. 이름은 잠정이며 결제 취소·탈퇴 환불 등은 필요할 때 추가함.
 *
 * <p>어느 잔액을 바꾸는 거래인지는 이름이 아니라 {@link #affectsCashback()}으로 판단함.
 */
public enum WalletTransactionType {
  // 충전: 모의계좌 → 지갑(데브머니 증가)
  CHARGE(false),
  // 환불: 지갑(데브머니 감소) → 모의계좌
  REFUND(false),
  // 결제 중 데브머니(충전금) 사용분
  PAYMENT_DEVMONEY(false),
  // 결제 중 캐시백 사용분(이름이 CASHBACK으로 시작하지 않아도 캐시백 잔액을 바꿈)
  PAYMENT_CASHBACK(true),
  // 캐시백 적립 지급(B의 지급 배치가 호출)
  CASHBACK_EARN(true),
  // 지급된 캐시백 회수(결제 취소 시, 잔액 음수 허용)
  CASHBACK_CLAWBACK(true);

  // 캐시백 잔액을 바꾸는 거래 여부. false면 데브머니 잔액을 바꿈
  private final boolean affectsCashback;

  WalletTransactionType(boolean affectsCashback) {
    this.affectsCashback = affectsCashback;
  }

  public boolean affectsCashback() {
    return affectsCashback;
  }
}
