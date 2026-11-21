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

/** 모의계좌 원장. INSERT만 하고 수정하지 않음. 지갑 원장(WalletTransaction)과 같은 모양. */
@Entity
@Table(
    name = "account_transaction",
    uniqueConstraints =
        // 같은 거래의 중복 기록 방지(충전 ID 등). ref_key가 null인 거래(가입 보너스)는 중복으로 보지 않음
        @UniqueConstraint(
            name = "uk_account_transaction_type_ref_key",
            columnNames = {"type", "ref_key"}),
    // 계좌별 거래 내역 조회용
    indexes =
        @Index(
            name = "idx_account_transaction_account_created",
            columnList = "account_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false, updatable = false)
  private Long accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30, updatable = false)
  private AccountTransactionType type;

  // 부호 있는 금액. 입금은 +, 차감은 -. 같은 계좌의 amount 합계가 잔액과 같음
  @Column(nullable = false, updatable = false)
  private long amount;

  // 이 거래 직후의 계좌 잔액
  @Column(name = "balance_after", nullable = false, updatable = false)
  private long balanceAfter;

  // 거래의 원인이 된 ID(충전 ID 등). 가입 보너스처럼 원인 행이 없으면 null
  @Column(name = "ref_key", updatable = false)
  private Long refKey;

  // 생성 시각. 엔티티가 만들지 않고 호출자가 Clock으로 만든 시각을 받음
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private AccountTransaction(
      Long accountId,
      AccountTransactionType type,
      long amount,
      long balanceAfter,
      Long refKey,
      LocalDateTime createdAt) {
    // 필수 값을 빠뜨리면 DB까지 가기 전에 여기서 막음
    this.accountId = Objects.requireNonNull(accountId, "accountId는 필수");
    this.type = Objects.requireNonNull(type, "type은 필수");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt은 필수");
    // 0원 거래는 원장에 남길 의미가 없고 호출 실수일 가능성이 높음
    if (amount == 0) {
      throw new IllegalArgumentException("amount는 0일 수 없음");
    }
    this.amount = amount;
    this.balanceAfter = balanceAfter;
    this.refKey = refKey;
  }
}
