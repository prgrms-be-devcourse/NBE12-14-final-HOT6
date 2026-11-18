package com.back.nbe1214finalhot6.domain.qr.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrCodeTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 9, 0);

  @Test
  @DisplayName("생성하면 대기 상태이고 만료 시각은 생성 시각 + 3분이다")
  void 생성시대기상태와만료시각() {
    QrCode qrCode = QrCode.createDynamic(1L, 5000L, NOW);

    assertThat(qrCode.getStatus()).isEqualTo(QrStatus.WAITING);
    assertThat(qrCode.getCreatedAt()).isEqualTo(NOW);
    assertThat(qrCode.getExpiresAt()).isEqualTo(NOW.plusMinutes(3));
    assertThat(qrCode.getToken()).isNotBlank();
  }

  @Test
  @DisplayName("만료 시각 1초 전까지는 대기, 만료 시각부터는 만료로 판정한다")
  void 만료경계판정() {
    QrCode qrCode = QrCode.createDynamic(1L, 5000L, NOW);
    LocalDateTime expiresAt = NOW.plusMinutes(3);

    assertThat(qrCode.statusAt(NOW)).isEqualTo(QrStatus.WAITING);
    assertThat(qrCode.statusAt(expiresAt.minusSeconds(1))).isEqualTo(QrStatus.WAITING);
    assertThat(qrCode.isExpired(expiresAt.minusSeconds(1))).isFalse();
    assertThat(qrCode.statusAt(expiresAt)).isEqualTo(QrStatus.EXPIRED);
    assertThat(qrCode.isExpired(expiresAt)).isTrue();
    assertThat(qrCode.statusAt(expiresAt.plusSeconds(1))).isEqualTo(QrStatus.EXPIRED);
  }
}
