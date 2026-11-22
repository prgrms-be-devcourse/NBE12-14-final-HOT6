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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 가맹점 지갑 원장. INSERT만 하고 수정하지 않음. */
@Entity
@Table(
    name = "merchant_wallet_transaction",
    uniqueConstraints =
        // 같은 월 정산 건의 중복 입금·출금 방지. 입금과 출금은 type이 달라 같은 refKey를 함께 쓸 수 있음
        @UniqueConstraint(
            name = "uk_merchant_wallet_transaction_type_ref_key",
            columnNames = {"type", "ref_key"}),
    indexes =
        @Index(
            name = "idx_merchant_wallet_transaction_wallet_created",
            columnList = "wallet_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantWalletTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "wallet_id", nullable = false, updatable = false)
  private Long walletId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30, updatable = false)
  private MerchantWalletTransactionType type;

  // 부호 있는 금액. 입금은 +, 차감은 -
  @Column(nullable = false, updatable = false)
  private long amount;

  // 이 거래 직후의 지갑 잔액
  @Column(name = "balance_after", nullable = false, updatable = false)
  private long balanceAfter;

  // 거래의 원인이 된 월 정산 건 ID. 정산 쪽이 정함
  @Column(name = "ref_key", nullable = false, updatable = false)
  private Long refKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private MerchantWalletTransaction(
      Long walletId,
      MerchantWalletTransactionType type,
      long amount,
      long balanceAfter,
      Long refKey,
      LocalDateTime createdAt) {
    this.walletId = Objects.requireNonNull(walletId, "walletId는 필수");
    this.type = Objects.requireNonNull(type, "type은 필수");
    this.refKey = Objects.requireNonNull(refKey, "refKey는 필수");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt은 필수");
    if (amount == 0) {
      throw new IllegalArgumentException("amount는 0일 수 없음");
    }
    this.amount = amount;
    this.balanceAfter = balanceAfter;
  }
}
