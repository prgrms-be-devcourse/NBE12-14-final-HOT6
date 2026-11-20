package com.back.nbe1214finalhot6.domain.wallet.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WalletTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);

  @Test
  @DisplayName("데브머니는 잔액보다 많이 차감할 수 없다")
  void devMoney_insufficient() {
    Wallet wallet = Wallet.create(1L, NOW);
    wallet.credit(1_000L, false, NOW);

    assertThatThrownBy(() -> wallet.debit(1_001L, false, false, NOW))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE));
    assertThat(wallet.getBalance()).isEqualTo(1_000L);
  }

  @Test
  @DisplayName("allowNegative=true여도 데브머니는 음수가 될 수 없다")
  void devMoney_neverNegative() {
    Wallet wallet = Wallet.create(1L, NOW);
    wallet.credit(1_000L, false, NOW);

    assertThatThrownBy(() -> wallet.debit(1_001L, false, true, NOW))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE));
  }

  @Test
  @DisplayName("캐시백 회수는 allowNegative=true일 때 음수가 되고, 이후 입금이 음수를 상계한다")
  void cashback_negativeThenOffset() {
    Wallet wallet = Wallet.create(1L, NOW);
    wallet.credit(100L, true, NOW);

    assertThat(wallet.debit(300L, true, true, NOW)).isEqualTo(-200L);
    assertThat(wallet.credit(150L, true, NOW)).isEqualTo(-50L);
    // 데브머니와는 상계하지 않음
    assertThat(wallet.getBalance()).isZero();
  }

  @Test
  @DisplayName("캐시백 잔액이 0이면 allowNegative=false인 사용은 거절된다")
  void cashback_zero_rejected() {
    Wallet wallet = Wallet.create(1L, NOW);

    assertThatThrownBy(() -> wallet.debit(1L, true, false, NOW))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(WalletErrorCode.INSUFFICIENT_CASHBACK));
  }

  @Test
  @DisplayName("0 이하 금액은 입금·차감 모두 거절하고 잔액은 그대로다")
  void nonPositiveAmount_rejected() {
    Wallet wallet = Wallet.create(1L, NOW);
    wallet.credit(1_000L, false, NOW);

    assertThatThrownBy(() -> wallet.credit(0L, false, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> wallet.credit(-1L, false, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    // 음수 차감이 입금이 되는 일을 막음
    assertThatThrownBy(() -> wallet.debit(-500L, false, false, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> wallet.debit(0L, true, true, NOW))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(wallet.getBalance()).isEqualTo(1_000L);
    assertThat(wallet.getCashbackBalance()).isZero();
  }

  @Test
  @DisplayName("캐시백 음수 허용 차감이 long 범위를 넘으면 예외가 나고 잔액은 그대로다")
  void cashbackDebit_overflow() {
    Wallet wallet = Wallet.create(1L, NOW);
    wallet.debit(Long.MAX_VALUE, true, true, NOW);
    long before = wallet.getCashbackBalance();

    assertThatThrownBy(() -> wallet.debit(Long.MAX_VALUE, true, true, NOW))
        .isInstanceOf(ArithmeticException.class);

    assertThat(wallet.getCashbackBalance()).isEqualTo(before);
  }

  @Test
  @DisplayName("탈퇴 처리하면 탈퇴 상태가 된다")
  void withdraw() {
    Wallet wallet = Wallet.create(1L, NOW);
    assertThat(wallet.isWithdrawn()).isFalse();

    wallet.withdraw(NOW.plusHours(1));

    assertThat(wallet.isWithdrawn()).isTrue();
    assertThat(wallet.getUpdatedAt()).isEqualTo(NOW.plusHours(1));
  }
}
