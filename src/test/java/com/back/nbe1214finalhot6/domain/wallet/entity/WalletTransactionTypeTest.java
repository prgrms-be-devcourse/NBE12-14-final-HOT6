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
}
