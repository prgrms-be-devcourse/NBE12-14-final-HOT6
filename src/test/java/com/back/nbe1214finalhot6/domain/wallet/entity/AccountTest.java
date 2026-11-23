package com.back.nbe1214finalhot6.domain.wallet.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

  @Test
  @DisplayName("출금하면 잔액이 줄고 출금 후 잔액을 돌려준다")
  void debit_reducesBalance() {
    Account account = Account.create(1L, NOW);

    long after = account.debit(300_000L);

    assertThat(after).isEqualTo(Account.INITIAL_BALANCE - 300_000L);
    assertThat(account.getBalance()).isEqualTo(after);
  }

  @Test
  @DisplayName("잔액과 같은 금액까지는 출금할 수 있다")
  void debit_exactBalance() {
    Account account = Account.create(1L, NOW);

    assertThat(account.debit(Account.INITIAL_BALANCE)).isZero();
  }

  @Test
  @DisplayName("잔액보다 많이 출금하면 WAL-009로 거절되고 잔액은 그대로다")
  void debit_insufficient() {
    Account account = Account.create(1L, NOW);

    assertThatThrownBy(() -> account.debit(Account.INITIAL_BALANCE + 1))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(WalletErrorCode.INSUFFICIENT_ACCOUNT_BALANCE));
    assertThat(account.getBalance()).isEqualTo(Account.INITIAL_BALANCE);
  }

  @Test
  @DisplayName("0 이하 금액은 거절한다")
  void debit_nonPositive() {
    Account account = Account.create(1L, NOW);

    assertThatThrownBy(() -> account.debit(0L)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.debit(-1L)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("입금하면 잔액이 늘고 입금 후 잔액을 돌려준다")
  void credit_increasesBalance() {
    Account account = Account.create(1L, NOW);

    long after = account.credit(300_000L);

    assertThat(after).isEqualTo(Account.INITIAL_BALANCE + 300_000L);
    assertThat(account.getBalance()).isEqualTo(after);
  }

  @Test
  @DisplayName("입금은 0 이하 금액을 거절하고, long 범위를 넘으면 예외가 나며 잔액은 그대로다")
  void credit_invalid() {
    Account account = Account.create(1L, NOW);

    assertThatThrownBy(() -> account.credit(0L)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.credit(-1L)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.credit(Long.MAX_VALUE)).isInstanceOf(ArithmeticException.class);
    assertThat(account.getBalance()).isEqualTo(Account.INITIAL_BALANCE);
  }
}
