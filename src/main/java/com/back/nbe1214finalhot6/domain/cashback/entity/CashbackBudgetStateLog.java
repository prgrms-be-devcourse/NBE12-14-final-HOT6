package com.back.nbe1214finalhot6.domain.cashback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 월 예산의 적립 상태 전이 이력. 상태가 바뀔 때마다 한 행을 남기며 INSERT only 다.
 *
 * <p>처리자는 자동 판정이면 null 이다.
 */
@Entity
@Table(
    name = "cashback_budget_state_log",
    indexes = @Index(name = "idx_cashback_budget_state_log_budget", columnList = "monthly_budget_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashbackBudgetStateLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "monthly_budget_id", nullable = false)
  private Long monthlyBudgetId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", nullable = false, length = 20)
  private BudgetStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 20)
  private BudgetStatus toStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private StateChangeReason reason;

  // 바뀔 당시 남은 예산 비율(소수 비율)
  @Column(name = "remaining_rate", nullable = false, precision = 5, scale = CashbackPolicy.RATE_SCALE)
  private BigDecimal remainingRate;

  // 자동 판정이면 null
  @Column(name = "operator_id")
  private Long operatorId;

  @Column(name = "occurred_at", nullable = false)
  private LocalDateTime occurredAt;

  private CashbackBudgetStateLog(
      Long monthlyBudgetId,
      BudgetStatus fromStatus,
      BudgetStatus toStatus,
      StateChangeReason reason,
      BigDecimal remainingRate,
      Long operatorId,
      LocalDateTime occurredAt) {
    this.monthlyBudgetId = monthlyBudgetId;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.reason = reason;
    this.remainingRate = remainingRate;
    this.operatorId = operatorId;
    this.occurredAt = occurredAt;
  }

  /** 자동 판정이나 예약 시각 도달로 바뀐 기록. */
  public static CashbackBudgetStateLog automatic(
      Long monthlyBudgetId,
      BudgetStatus fromStatus,
      BudgetStatus toStatus,
      StateChangeReason reason,
      BigDecimal remainingRate,
      LocalDateTime occurredAt) {
    return new CashbackBudgetStateLog(
        monthlyBudgetId, fromStatus, toStatus, reason, remainingRate, null, occurredAt);
  }
}
