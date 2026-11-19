package com.back.nbe1214finalhot6.domain.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 지갑 원장. INSERT만 하고 수정하지 않음. 취소는 반대 방향의 새 행으로 남김. */
@Entity
@Table(
    name = "wallet_transaction",
    uniqueConstraints =
        // 같은 요청의 중복 반영 방지. 같은 적립 건에 지급과 회수가 모두 생길 수 있어 type과 함께 묶음
        @UniqueConstraint(
            name = "uk_wallet_transaction_type_ref_key",
            columnNames = {"type", "ref_key"}),
    // 지갑별 거래 내역 조회용
    indexes =
        @Index(
            name = "idx_wallet_transaction_wallet_created",
            columnList = "wallet_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "wallet_id", nullable = false, updatable = false)
  private Long walletId;

  // 거래의 원인이 된 충전·환불·탈퇴 정산·결제 ID. 해당하는 것만 채우고 나머지는 null
  @Column(name = "charge_id", updatable = false)
  private Long chargeId;

  @Column(name = "refund_id", updatable = false)
  private Long refundId;

  @Column(name = "settlement_id", updatable = false)
  private Long settlementId;

  @Column(name = "payment_id", updatable = false)
  private Long paymentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30, updatable = false)
  private WalletTransactionType type;

  // 이 거래로 움직인 금액
  @Column(nullable = false, updatable = false)
  private long amount;

  // 이 거래 직후의 잔액. type.affectsCashback()이 true면 캐시백 잔액, false면 데브머니 잔액
  @Column(name = "balance_after", nullable = false, updatable = false)
  private long balanceAfter;

  // 멱등 키. 적립 건 ID, 결제 ID 등 호출자가 정함.
  // 충전·환불은 null이며(유니크에서 null끼리는 중복으로 보지 않음), 중복 방지는 charge·refund의 idempotency_key가 맡음
  @Column(name = "ref_key", updatable = false)
  private Long refKey;

  // 생성 시각. 엔티티가 만들지 않고 서비스가 Clock으로 만든 시각을 받음
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private WalletTransaction(
      Long walletId,
      Long chargeId,
      Long refundId,
      Long settlementId,
      Long paymentId,
      WalletTransactionType type,
      long amount,
      long balanceAfter,
      Long refKey,
      LocalDateTime createdAt) {
    this.createdAt = createdAt;
    this.walletId = walletId;
    this.chargeId = chargeId;
    this.refundId = refundId;
    this.settlementId = settlementId;
    this.paymentId = paymentId;
    this.type = type;
    this.amount = amount;
    this.balanceAfter = balanceAfter;
    this.refKey = refKey;
  }
}
