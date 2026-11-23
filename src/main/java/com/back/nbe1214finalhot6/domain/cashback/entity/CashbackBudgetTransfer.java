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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 월 예산 이월 이력. 한 달의 남은 예산을 다음 달로 넘길 때마다 한 행을 남기며 INSERT only 다.
 *
 * <p>처리자는 자동 이월이면 null 이고, 인증이 머지되기 전까지는 수동 이월도 null 이다.
 */
@Entity
@Table(
    name = "cashback_budget_transfer",
    indexes = @Index(name = "idx_cashback_budget_transfer_from", columnList = "from_monthly_budget_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashbackBudgetTransfer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "from_monthly_budget_id", nullable = false)
  private Long fromMonthlyBudgetId;

  @Column(name = "to_monthly_budget_id", nullable = false)
  private Long toMonthlyBudgetId;

  @Column(nullable = false)
  private long amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BudgetTransferType type;

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  private CashbackBudgetTransfer(
      Long fromMonthlyBudgetId,
      Long toMonthlyBudgetId,
      long amount,
      BudgetTransferType type,
      Long createdBy,
      LocalDateTime createdAt) {
    this.fromMonthlyBudgetId = fromMonthlyBudgetId;
    this.toMonthlyBudgetId = toMonthlyBudgetId;
    this.amount = amount;
    this.type = type;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  /** 운영자가 직접 이월한 기록. 처리자는 인증이 머지되기 전까지 null 이다. */
  public static CashbackBudgetTransfer manual(
      Long fromMonthlyBudgetId, Long toMonthlyBudgetId, long amount, LocalDateTime createdAt) {
    return new CashbackBudgetTransfer(
        fromMonthlyBudgetId, toMonthlyBudgetId, amount, BudgetTransferType.MANUAL, null, createdAt);
  }
}
