package com.back.nbe1214finalhot6.domain.qr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "qr_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QrCode {

  public static final Duration VALID_DURATION = Duration.ofMinutes(3);
  public static final long MAX_AMOUNT = 1_000_000L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 36)
  private String token;

  @Column(name = "merchant_id", nullable = false)
  private Long merchantId;

  @Column(nullable = false)
  private Long amount;

  // DB 에는 WAITING, USED 만 저장한다. 만료(EXPIRED)는 statusAt 에서 계산해서 돌려준다.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private QrStatus status;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private QrCode(Long merchantId, Long amount, LocalDateTime now) {
    this.token = UUID.randomUUID().toString();
    this.merchantId = merchantId;
    this.amount = amount;
    this.status = QrStatus.WAITING;
    this.createdAt = now;
    this.expiresAt = now.plus(VALID_DURATION);
  }

  public static QrCode createDynamic(Long merchantId, Long amount, LocalDateTime now) {
    return new QrCode(merchantId, amount, now);
  }

  public boolean isExpired(LocalDateTime now) {
    return !expiresAt.isAfter(now);
  }

  public QrStatus statusAt(LocalDateTime now) {
    if (status == QrStatus.USED) {
      return QrStatus.USED;
    }
    return isExpired(now) ? QrStatus.EXPIRED : QrStatus.WAITING;
  }
}
