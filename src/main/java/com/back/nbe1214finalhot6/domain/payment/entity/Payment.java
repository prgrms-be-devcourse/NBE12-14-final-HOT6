package com.back.nbe1214finalhot6.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "payment",
    uniqueConstraints =
        @UniqueConstraint( //유니크 복합키 설정 : 지갑id & 멱등키
            name = "uk_payment_wallet_idempotency",
            columnNames = {"wallet_id", "idempotency_key"}))
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Getter
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "wallet_id", nullable = false)
  private Long walletId;

  @Column(name = "merchant_id", nullable = false)
  private Long merchantId;

  @Column(name = "qr_id")
  private Long qrId;

  @Column(name = "idempotency_key", nullable = false, length = 100)
  private String idempotencyKey;

  // 총 결제액 = 데브머니 사용분 + 캐시백 사용분
  @Column(nullable = false)
  private Long amount;

  @Column(name = "wallet_used_amount", nullable = false)
  private Long walletUsedAmount;

  // 캐시백은 1원 단위로만 사용
  @Column(name = "cashback_used_amount", nullable = false)
  private Long cashbackUsedAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  @Column(name = "failure_code", length = 50)
  private String failureCode;

  // 캐시백 정책이 없으면 null
  @Column(name = "applied_policy_id")
  private Long appliedPolicyId;

  @Column(name = "applied_rate", precision = 5, scale = 4)
  private BigDecimal appliedRate;

  // 적립액은 정수 원 단위 (소수점 버림)
  @Column(name = "cashback_earned_amount")
  private Long cashbackEarnedAmount;

  // 서버가 최초 접수한 시각 (적립 요율·월 한도 기준)
  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "canceled_at")
  private LocalDateTime canceledAt;

  // 취소한 가맹점주
  @Column(name = "canceled_by")
  private Long canceledBy;
}
