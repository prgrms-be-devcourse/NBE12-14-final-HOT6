package com.back.nbe1214finalhot6.domain.wallet.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountTransactionTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

  @Test
  @DisplayName("부호 있는 금액과 거래 후 잔액을 그대로 보관한다")
  void keepsSignedAmount() {
    AccountTransaction ledger =
        AccountTransaction.builder()
            .accountId(1L)
            .type(AccountTransactionType.SIGNUP_BONUS)
            .amount(2_000_000L)
            .balanceAfter(2_000_000L)
            .createdAt(NOW)
            .build();

    assertThat(ledger.getAmount()).isEqualTo(2_000_000L);
    assertThat(ledger.getBalanceAfter()).isEqualTo(2_000_000L);
    assertThat(ledger.getType()).isEqualTo(AccountTransactionType.SIGNUP_BONUS);
  }

  @Test
  @DisplayName("0원 거래는 만들 수 없다")
  void zeroAmount_rejected() {
    assertThatThrownBy(
            () ->
                AccountTransaction.builder()
                    .accountId(1L)
                    .type(AccountTransactionType.SIGNUP_BONUS)
                    .amount(0L)
                    .createdAt(NOW)
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("필수 값이 없으면 생성 시점에 예외가 난다")
  void requiredValues() {
    assertThatThrownBy(
            () ->
                AccountTransaction.builder()
                    .type(AccountTransactionType.SIGNUP_BONUS)
                    .amount(1L)
                    .createdAt(NOW)
                    .build())
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> AccountTransaction.builder().accountId(1L).amount(1L).createdAt(NOW).build())
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                AccountTransaction.builder()
                    .accountId(1L)
                    .type(AccountTransactionType.SIGNUP_BONUS)
                    .amount(1L)
                    .build())
        .isInstanceOf(NullPointerException.class);
  }
}
