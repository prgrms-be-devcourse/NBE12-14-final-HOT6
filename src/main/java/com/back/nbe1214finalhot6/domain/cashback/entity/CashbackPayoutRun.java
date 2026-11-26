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
 * 지급 배치 실행 기록. 운영자가 실행 결과를 확인하는 용도이며 INSERT only 다. 수동 실행은 항상, 자동 실행은 지급이나 실패가 있을 때만 남긴다.
 */
@Entity
@Table(
    name = "cashback_payout_run",
    indexes = @Index(name = "idx_cashback_payout_run_ran_at", columnList = "ran_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashbackPayoutRun {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 배치를 시작한 시각. */
  @Column(name = "ran_at", nullable = false)
  private LocalDateTime ranAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_type", nullable = false, length = 20)
  private PayoutTrigger triggerType;

  @Column(name = "paid_count", nullable = false)
  private int paidCount;

  @Column(name = "skipped_count", nullable = false)
  private int skippedCount;

  @Column(name = "failed_count", nullable = false)
  private int failedCount;

  private CashbackPayoutRun(
      LocalDateTime ranAt, PayoutTrigger triggerType, int paidCount, int skippedCount, int failedCount) {
    this.ranAt = ranAt;
    this.triggerType = triggerType;
    this.paidCount = paidCount;
    this.skippedCount = skippedCount;
    this.failedCount = failedCount;
  }

  public static CashbackPayoutRun of(
      LocalDateTime ranAt, PayoutTrigger triggerType, int paid, int skipped, int failed) {
    return new CashbackPayoutRun(ranAt, triggerType, paid, skipped, failed);
  }
}
