package com.back.nbe1214finalhot6.domain.wallet.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefundTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 22, 10, 0);

  @Test
  @DisplayName("지갑, 금액, 멱등키, 생성 시각을 그대로 보관한다")
  void create() {
    Refund refund = Refund.create(1L, 100_000L, "key-1", NOW);

    assertThat(refund.getWalletId()).isEqualTo(1L);
    assertThat(refund.getAmount()).isEqualTo(100_000L);
    assertThat(refund.getIdempotencyKey()).isEqualTo("key-1");
    assertThat(refund.getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("0 이하 금액이나 비어 있는 멱등키는 만들 수 없다")
  void invalidValues() {
    assertThatThrownBy(() -> Refund.create(1L, 0L, "key-1", NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Refund.create(1L, 1L, " ", NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Refund.create(1L, 1L, null, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("지갑 ID나 생성 시각이 없으면 생성 시점에 예외가 난다")
  void requiredValues() {
    assertThatThrownBy(() -> Refund.create(null, 1L, "key-1", NOW))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Refund.create(1L, 1L, "key-1", null))
        .isInstanceOf(NullPointerException.class);
  }
}
