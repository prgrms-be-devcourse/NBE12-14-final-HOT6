package com.back.nbe1214finalhot6.domain.payment.mock;

// TODO: C의 WalletService 연동 후 삭제 (거래 type 이름은 C가 정한다)
// C 의 제안(auth-sl-1118.md 8절): CHARGE, REFUND, PAYMENT, PAYMENT_CANCEL, CASHBACK_EARN, CASHBACK_CLAWBACK
// 결제가 지금 쓰는 것은 PAYMENT 뿐이다. 취소 구현 때 PAYMENT_CANCEL 을 추가한다.
public enum WalletTxType {
  PAYMENT
}
