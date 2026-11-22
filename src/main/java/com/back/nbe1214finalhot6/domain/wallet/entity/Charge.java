package com.back.nbe1214finalhot6.domain.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모의계좌에서 지갑으로 한 충전 1건. INSERT만 하고 수정하지 않음.
 *
 * <p>같은 멱등키의 재요청을 알아보려고 남기며, 계좌·지갑 원장의 ref_key로 쓰는 충전 ID를 만듦. 토스 결제창 연동(W6)에서 상태·결제 정보를 추가함.
 */
@Entity
@Table(
    name = "charge",
    uniqueConstraints =
        // 같은 지갑의 같은 멱등키는 한 번만 처리
        @UniqueConstraint(
            name = "uk_charge_wallet_idempotency_key",
            columnNames = {"wallet_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Charge {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "wallet_id", nullable = false, updatable = false)
  private Long walletId;

  // 충전액(원). 항상 양수
  @Column(nullable = false, updatable = false)
  private long amount;

  @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
  private String idempotencyKey;

  // 생성 시각. 엔티티가 만들지 않고 서비스가 Clock으로 만든 시각을 받음
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private Charge(Long walletId, long amount, String idempotencyKey, LocalDateTime createdAt) {
    // 필수 값을 빠뜨리면 DB까지 가기 전에 여기서 막음
    this.walletId = Objects.requireNonNull(walletId, "walletId는 필수");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt은 필수");
    if (amount <= 0) {
      throw new IllegalArgumentException("amount는 양수여야 함: " + amount);
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey는 필수");
    }
    this.amount = amount;
    this.idempotencyKey = idempotencyKey;
  }

  public static Charge create(
      Long walletId, long amount, String idempotencyKey, LocalDateTime createdAt) {
    return new Charge(walletId, amount, idempotencyKey, createdAt);
  }
}
