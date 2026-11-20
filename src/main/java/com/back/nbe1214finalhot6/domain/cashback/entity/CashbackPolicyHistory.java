package com.back.nbe1214finalhot6.domain.cashback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정책(요율 구간) 변경 이력. 구간을 만들거나 값을 바꿀 때마다 변경 전후 값을 한 행으로 남긴다.
 *
 * <p>변경자는 인증이 머지되기 전까지 null 이다.
 */
@Entity
@Table(name = "cashback_policy_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashbackPolicyHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "policy_id", nullable = false)
  private Long policyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 20)
  private PolicyHistoryAction action;

  @Column(name = "changed_by")
  private Long changedBy;

  @Column(name = "changed_at", nullable = false)
  private LocalDateTime changedAt;

  // 변경 전 값. CREATE 면 모두 null
  @Column(name = "before_rate", precision = 5, scale = CashbackPolicy.RATE_SCALE)
  private BigDecimal beforeRate;

  @Column(name = "before_starts_at")
  private LocalDateTime beforeStartsAt;

  @Column(name = "before_ends_at")
  private LocalDateTime beforeEndsAt;

  // 변경 후 값
  @Column(name = "after_rate", nullable = false, precision = 5, scale = CashbackPolicy.RATE_SCALE)
  private BigDecimal afterRate;

  @Column(name = "after_starts_at", nullable = false)
  private LocalDateTime afterStartsAt;

  @Column(name = "after_ends_at")
  private LocalDateTime afterEndsAt;

  private CashbackPolicyHistory(
      PolicyHistoryAction action,
      Long changedBy,
      LocalDateTime changedAt,
      CashbackPolicy before,
      CashbackPolicy after) {
    this.policyId = after.getId();
    this.action = action;
    this.changedBy = changedBy;
    this.changedAt = changedAt;
    if (before != null) {
      this.beforeRate = before.getRate();
      this.beforeStartsAt = before.getStartsAt();
      this.beforeEndsAt = before.getEndsAt();
    }
    this.afterRate = after.getRate();
    this.afterStartsAt = after.getStartsAt();
    this.afterEndsAt = after.getEndsAt();
  }

  /** 구간 생성 기록. 이미 저장되어 id 가 있는 구간을 넘긴다. */
  public static CashbackPolicyHistory created(
      CashbackPolicy policy, Long changedBy, LocalDateTime changedAt) {
    return new CashbackPolicyHistory(
        PolicyHistoryAction.CREATE, changedBy, changedAt, null, policy);
  }

  /**
   * 구간 변경 기록.
   *
   * @param before 변경 전 값을 복사해 둔 구간. 저장하지 않는 스냅샷이다
   * @param after 변경된 구간
   */
  public static CashbackPolicyHistory updated(
      CashbackPolicy before, CashbackPolicy after, Long changedBy, LocalDateTime changedAt) {
    return new CashbackPolicyHistory(
        PolicyHistoryAction.UPDATE, changedBy, changedAt, before, after);
  }
}
