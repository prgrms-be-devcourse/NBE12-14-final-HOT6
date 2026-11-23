package com.back.nbe1214finalhot6.domain.wallet.entity;

/**
 * 지갑 원장의 거래 종류. 이름은 잠정이며 탈퇴 환불 등은 필요할 때 추가함.
 *
 * <p>어느 잔액을 바꾸는지는 이름이 아니라 {@link #affectsCashback()}으로, 입금인지 차감인지는 {@link
 * #isCredit()}으로 판단함.
 */
public enum WalletTransactionType {
  // 충전: 모의계좌 → 지갑(데브머니 증가)
  CHARGE(false, true),
  // 환불: 지갑(데브머니 감소) → 모의계좌
  REFUND(false, false),
  // 결제 중 데브머니(충전금) 사용분
  PAYMENT_DEVMONEY(false, false),
  // 결제 중 캐시백 사용분(이름이 CASHBACK으로 시작하지 않아도 캐시백 잔액을 바꿈)
  PAYMENT_CASHBACK(true, false),
  // 결제 취소 시 데브머니 사용분 환급
  PAYMENT_CANCEL_DEVMONEY(false, true),
  // 결제 취소 시 캐시백 사용분 환급
  PAYMENT_CANCEL_CASHBACK(true, true),
  // 캐시백 적립 지급(B의 지급 배치가 호출)
  CASHBACK_EARN(true, true),
  // 지급된 캐시백 회수(결제 취소 시, 잔액 음수 허용)
  CASHBACK_CLAWBACK(true, false);

  // 캐시백 잔액을 바꾸는 거래 여부. false면 데브머니 잔액을 바꿈
  private final boolean affectsCashback;
  // 입금 거래 여부. false면 차감 거래
  private final boolean credit;

  WalletTransactionType(boolean affectsCashback, boolean credit) {
    this.affectsCashback = affectsCashback;
    this.credit = credit;
  }

  public boolean affectsCashback() {
    return affectsCashback;
  }

  public boolean isCredit() {
    return credit;
  }
}
