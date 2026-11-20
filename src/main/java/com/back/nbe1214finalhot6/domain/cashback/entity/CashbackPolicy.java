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
import java.time.temporal.ChronoUnit;
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
   * @throws BusinessException 요율이 0 이하이거나 1 초과이거나 소수 넷째 자리를 넘을 때, 종료가 시작보다 늦지 않을 때,
   *     시작이 현재(분 단위)보다 과거일 때
   */
  public static CashbackPolicy create(
      BigDecimal rate, LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime now) {
    validate(rate, startsAt, endsAt, now);
    return new CashbackPolicy(rate.setScale(RATE_SCALE), startsAt, endsAt, now);
  }

  /** 변경 전 값을 기록하기 위한 복사본. 저장하지 않는다. */
  public CashbackPolicy snapshot() {
    CashbackPolicy copy = new CashbackPolicy(rate, startsAt, endsAt, createdAt);
    copy.id = id;
    return copy;
  }

  /**
   * 종료 시각을 앞당긴다. 요율 인하 때 현재 구간을 적용 시각에 끝내는 데 쓴다.
   *
   * @throws BusinessException 새 종료가 시작보다 늦지 않을 때, 기존 종료보다 늦을 때
   */
  public void shortenEnd(LocalDateTime newEndsAt) {
    if (newEndsAt == null
        || !newEndsAt.isAfter(startsAt)
        || (endsAt != null && newEndsAt.isAfter(endsAt))) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_POLICY, "구간의 종료 시각은 시작 이후이면서 기존 종료 이전이어야 합니다");
    }
    this.endsAt = newEndsAt;
  }

  /**
   * 요율 형식을 검사한다. 정책 구간과 월 기본 요율이 같은 규칙을 쓴다.
   *
   * @throws BusinessException 없거나 0 이하이거나 1 초과이거나 소수 넷째 자리를 넘을 때
   */
  public static void validateRate(BigDecimal rate) {
    if (rate == null) {
      throw new BusinessException(CashbackErrorCode.INVALID_POLICY);
    }
    if (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) > 0) {
      throw new BusinessException(CashbackErrorCode.INVALID_POLICY, "요율은 0 초과 1 이하여야 합니다");
    }
    if (rate.stripTrailingZeros().scale() > RATE_SCALE) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_POLICY, "요율은 소수 넷째 자리까지만 입력할 수 있습니다");
    }
  }

  private static void validate(
      BigDecimal rate, LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime now) {
    validateRate(rate);
    if (startsAt == null) {
      throw new BusinessException(CashbackErrorCode.INVALID_POLICY);
    }
    // 과거에 비어 있던 기간을 나중에 채우면 이력이 실제 적용 결과와 달라진다. "지금 시작" 은 분 단위로 허용한다.
    if (startsAt.isBefore(now.truncatedTo(ChronoUnit.MINUTES))) {
      throw new BusinessException(CashbackErrorCode.INVALID_POLICY, "시작 시각은 현재 이후여야 합니다");
    }
    if (endsAt != null && !endsAt.isAfter(startsAt)) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_POLICY, "종료 시각은 시작 시각보다 늦어야 합니다");
    }
  }
}
