package com.back.nbe1214finalhot6.domain.cashback.entity;

import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 적립 건. 결제 한 건에 하나이며, 적립 정보의 기준 테이블이다. 제외된 결제도 사유와 함께 행을 남긴다.
 *
 * <p>요율은 결제를 접수한 시각의 값을 저장해 두고, 이후 정책이 바뀌어도 이 값을 쓴다. 결제 ID 가 유니크라서 같은 결제로 다시 호출해도 한 건만 생긴다.
 */
@Entity
@Table(
    name = "cashback_earning",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_cashback_earning_payment",
            columnNames = {"payment_id"}),
    indexes = {
      @Index(name = "idx_cashback_earning_status_payable", columnList = "status, payable_at"),
      @Index(name = "idx_cashback_earning_wallet_requested", columnList = "wallet_id, requested_at"),
      // 지갑을 가리지 않고 접수 기간으로 합산한다(소진 예측, 예측 오차)
      @Index(name = "idx_cashback_earning_requested", columnList = "requested_at")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashbackEarning {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 다른 도메인의 엔티티는 연관관계 없이 ID 로만 참조한다
  @Column(name = "payment_id", nullable = false)
  private Long paymentId;

  @Column(name = "wallet_id", nullable = false)
  private Long walletId;

  // 적립 기준 금액(데브머니로 결제한 금액)
  @Column(name = "wallet_used_amount", nullable = false)
  private long walletUsedAmount;

  @Column(name = "applied_rate", nullable = false, precision = 5, scale = CashbackPolicy.RATE_SCALE)
  private BigDecimal appliedRate;

  // 달 중 인하 구간이 적용됐을 때만 있다. 월 기본 요율이 적용됐거나 제외면 null
  @Column(name = "applied_policy_id")
  private Long appliedPolicyId;

  // 적립액. 제외 건은 0
  @Column(nullable = false)
  private long amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EarningStatus status;

  // 제외 건에만 있다
  @Enumerated(EnumType.STRING)
  @Column(name = "exclusion_reason", length = 30)
  private CashbackExclusionReason exclusionReason;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  // 지급 예정 시각. 제외 건은 null
  @Column(name = "payable_at")
  private LocalDateTime payableAt;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  @Column(name = "canceled_at")
  private LocalDateTime canceledAt;

  // 월 한도를 차감한 달의 1일. 예산을 확보한 달과 같고, 취소 때 이 달에서 되돌린다. 제외 건은 null
  @Column(name = "limit_month")
  private LocalDate limitMonth;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private CashbackEarning(
      Long paymentId,
      Long walletId,
      long walletUsedAmount,
      LocalDateTime requestedAt,
      LocalDateTime createdAt) {
    this.paymentId = paymentId;
    this.walletId = walletId;
    this.walletUsedAmount = walletUsedAmount;
    this.requestedAt = requestedAt;
    this.createdAt = createdAt;
  }

  /** 적립 예정 건을 만든다. 한도와 예산을 이미 확보한 뒤에 호출한다. */
  public static CashbackEarning pending(
      Long paymentId,
      Long walletId,
      long walletUsedAmount,
      LocalDateTime requestedAt,
      BigDecimal appliedRate,
      Long appliedPolicyId,
      long amount,
      LocalDateTime payableAt,
      LocalDate limitMonth,
      LocalDateTime now) {
    CashbackEarning earning =
        new CashbackEarning(paymentId, walletId, walletUsedAmount, requestedAt, now);
    earning.appliedRate = appliedRate;
    earning.appliedPolicyId = appliedPolicyId;
    earning.amount = amount;
    earning.status = EarningStatus.PENDING;
    earning.payableAt = payableAt;
    earning.limitMonth = limitMonth;
    return earning;
  }

  /** 적립하지 않은 건을 만든다. 적립액과 요율은 0 이다. */
  public static CashbackEarning excluded(
      Long paymentId,
      Long walletId,
      long walletUsedAmount,
      LocalDateTime requestedAt,
      CashbackExclusionReason reason,
      LocalDateTime now) {
    CashbackEarning earning =
        new CashbackEarning(paymentId, walletId, walletUsedAmount, requestedAt, now);
    earning.appliedRate = BigDecimal.ZERO.setScale(CashbackPolicy.RATE_SCALE);
    earning.amount = 0L;
    earning.status = EarningStatus.EXCLUDED;
    earning.exclusionReason = reason;
    return earning;
  }
}
