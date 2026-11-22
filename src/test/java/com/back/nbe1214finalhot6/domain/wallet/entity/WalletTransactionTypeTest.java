package com.back.nbe1214finalhot6.domain.wallet.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WalletTransactionTypeTest {

  @Test
  @DisplayName("결제의 캐시백 사용분은 이름이 달라도 캐시백 잔액을 바꾸는 거래다")
  void paymentCashback_affectsCashback() {
    assertThat(WalletTransactionType.PAYMENT_CASHBACK.affectsCashback()).isTrue();
  }

  @Test
  @DisplayName("충전·환불·결제의 데브머니 사용분은 데브머니 잔액을 바꾸는 거래다")
  void devMoneyTypes_doNotAffectCashback() {
    assertThat(WalletTransactionType.CHARGE.affectsCashback()).isFalse();
    assertThat(WalletTransactionType.REFUND.affectsCashback()).isFalse();
    assertThat(WalletTransactionType.PAYMENT_DEVMONEY.affectsCashback()).isFalse();
  }

  @Test
  @DisplayName("캐시백 적립 지급과 회수는 캐시백 잔액을 바꾸는 거래다")
  void cashbackEarnAndClawback_affectCashback() {
    assertThat(WalletTransactionType.CASHBACK_EARN.affectsCashback()).isTrue();
    assertThat(WalletTransactionType.CASHBACK_CLAWBACK.affectsCashback()).isTrue();
  }

  @Test
  @DisplayName("결제 취소 환급은 사용했던 잔액 종류와 같은 잔액을 늘리는 입금 거래다")
  void cancelTypes() {
    assertThat(WalletTransactionType.PAYMENT_CANCEL_DEVMONEY.affectsCashback()).isFalse();
    assertThat(WalletTransactionType.PAYMENT_CANCEL_CASHBACK.affectsCashback()).isTrue();
    assertThat(WalletTransactionType.PAYMENT_CANCEL_DEVMONEY.isCredit()).isTrue();
    assertThat(WalletTransactionType.PAYMENT_CANCEL_CASHBACK.isCredit()).isTrue();
  }

  @Test
  @DisplayName("입금 type과 차감 type이 정해져 있다")
  void direction() {
    assertThat(WalletTransactionType.CHARGE.isCredit()).isTrue();
    assertThat(WalletTransactionType.CASHBACK_EARN.isCredit()).isTrue();
    assertThat(WalletTransactionType.REFUND.isCredit()).isFalse();
    assertThat(WalletTransactionType.PAYMENT_DEVMONEY.isCredit()).isFalse();
    assertThat(WalletTransactionType.PAYMENT_CASHBACK.isCredit()).isFalse();
    assertThat(WalletTransactionType.CASHBACK_CLAWBACK.isCredit()).isFalse();
  }
}
