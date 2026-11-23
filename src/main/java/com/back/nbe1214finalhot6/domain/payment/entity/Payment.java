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

  // 이용자가 스캔한 QR 토큰(UUID). T1 에서 저장해 두고 요청 내용 비교와 QR 사용 처리에 쓴다
  @Column(name = "qr_token", length = 64)
  private String qrToken;

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

  // 캐시백 지급 예정 시각 (적립 없으면 null)
  @Column(name = "cashback_payable_at")
  private LocalDateTime cashbackPayableAt;

  // 적립 제외 사유 (NONE, NOT_ELIGIBLE, BUDGET_EXHAUSTED, MONTHLY_LIMIT_EXCEEDED)
  @Column(name = "cashback_exclusion_reason", length = 30)
  private String cashbackExclusionReason;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "canceled_at")
  private LocalDateTime canceledAt;

  // 취소한 가맹점주
  @Column(name = "canceled_by")
  private Long canceledBy;

  // T3 실패 기록. 처리 중(PROCESSING)인 건만 FAILED 로 바꾸고 사유 코드를 남긴다.
  // failureCode 는 던져진 BusinessException 의 코드(예: PAY-002, QR-003)를 그대로 받는다.
  public void failure(String failureCode) {
    if (this.status != PaymentStatus.PROCESSING) {
      throw new IllegalStateException("처리 중인 결제만 실패 처리할 수 있습니다. 현재 상태: " + this.status);
    }
    if (failureCode == null || failureCode.isBlank()) {
      throw new IllegalArgumentException("실패 코드는 비어 있을 수 없습니다");
    }
    this.status = PaymentStatus.FAILED;
    this.failureCode = failureCode;
  }

  public void complete(
      long walletUsed,
      long cashbackUsed,
      Long policyId,
      BigDecimal rate,
      long earned,
      LocalDateTime payableAt,
      String exclusionReason,
      LocalDateTime completedAt)
  {
      this.status = PaymentStatus.COMPLETED;
      this.walletUsedAmount = walletUsed;
      this.cashbackUsedAmount = cashbackUsed;
      this.appliedPolicyId = policyId;
      this.appliedRate = rate;
      this.cashbackEarnedAmount = earned;
      this.cashbackPayableAt = payableAt;
      this.cashbackExclusionReason = exclusionReason;
      this.completedAt = completedAt;
  }

  public void cancel(LocalDateTime canceledAt) {
    this.status = PaymentStatus.CANCELED;
    this.canceledAt = canceledAt;
  }
}
