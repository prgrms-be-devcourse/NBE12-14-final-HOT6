package com.back.nbe1214finalhot6.domain.wallet.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WalletTransactionTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 10, 0);

  @Test
  @DisplayName("0원 거래는 만들 수 없다")
  void zeroAmount_rejected() {
    assertThatThrownBy(() -> builder().amount(0L).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("walletId, type, createdAt 중 하나라도 빠지면 만들 수 없다")
  void requiredValues_rejected() {
    assertThatThrownBy(() -> builder().walletId(null).build())
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> builder().type(null).build())
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> builder().createdAt(null).build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("부호 있는 금액은 그대로 보관된다")
  void signedAmount_kept() {
    assertThat(builder().amount(5_000L).build().getAmount()).isEqualTo(5_000L);
    assertThat(builder().amount(-5_000L).build().getAmount()).isEqualTo(-5_000L);
  }

  // 모든 필수 값을 채운 기본 빌더. 테스트마다 바꿀 값만 덮어씀
  private WalletTransaction.WalletTransactionBuilder builder() {
    return WalletTransaction.builder()
        .walletId(1L)
        .type(WalletTransactionType.CHARGE)
        .amount(1_000L)
        .balanceAfter(1_000L)
        .createdAt(NOW);
  }
}
