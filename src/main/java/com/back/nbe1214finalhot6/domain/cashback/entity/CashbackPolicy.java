package com.back.nbe1214finalhot6.domain.cashback.entity;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 캐시백 정책. 요율과 적용 기간을 가진다.
 *
 * <p>기간은 {@code [startsAt, endsAt)} 반개구간이다. {@code endsAt} 이 없으면 무기한이다.
 */
@Entity
@Table(name = "cashback_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashbackPolicy {

  /** 요율은 소수 비율(3% = 0.03)이며 소수 넷째 자리까지 저장한다. */
  public static final int RATE_SCALE = 4;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, precision = 5, scale = RATE_SCALE)
  private BigDecimal rate;

  @Column(name = "starts_at", nullable = false)
  private LocalDateTime startsAt;

  // null 이면 무기한
  @Column(name = "ends_at")
  private LocalDateTime endsAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private CashbackPolicy(
      BigDecimal rate, LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime createdAt) {
    this.rate = rate;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.createdAt = createdAt;
  }

  /**
   * 정책을 만든다.
   *
   * @throws BusinessException 요율이 0 이하이거나 1 초과이거나 소수 넷째 자리를 넘을 때, 종료가 시작보다 늦지 않을 때
   */
  public static CashbackPolicy create(
      BigDecimal rate, LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime now) {
    validate(rate, startsAt, endsAt);
    return new CashbackPolicy(rate.setScale(RATE_SCALE), startsAt, endsAt, now);
  }

  private static void validate(BigDecimal rate, LocalDateTime startsAt, LocalDateTime endsAt) {
    if (rate == null || startsAt == null) {
      throw new BusinessException(CashbackErrorCode.INVALID_POLICY);
    }
    if (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) > 0) {
      throw new BusinessException(CashbackErrorCode.INVALID_POLICY, "요율은 0 초과 1 이하여야 합니다");
    }
    if (rate.stripTrailingZeros().scale() > RATE_SCALE) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_POLICY, "요율은 소수 넷째 자리까지만 입력할 수 있습니다");
    }
    if (endsAt != null && !endsAt.isAfter(startsAt)) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_POLICY, "종료 시각은 시작 시각보다 늦어야 합니다");
    }
  }
}
