package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 캐시백 적립 결과. 결제 도메인은 이 값을 결제 행에 저장하고 영수증에 쓴다.
 *
 * <p>적립은 전액 또는 0원이다. 0원이면 {@code exclusionReason} 이 사유를 알려 준다.
 *
 * @param earnedAmount 적립액(원). 제외면 0
 * @param appliedRate 적용한 요율. 제외면 0
 * @param appliedPolicyId 적용한 정책 ID. 없으면 null
 * @param payableAt 지급 예정 시각. 제외면 null
 * @param exclusionReason 적립 제외 사유. 적립됐으면 {@link ExclusionReason#NONE}
 */
public record ReserveResult(
    long earnedAmount,
    BigDecimal appliedRate,
    Long appliedPolicyId,
    LocalDateTime payableAt,
    ExclusionReason exclusionReason) {

  /** 적립 없이 사유만 알리는 결과. */
  public static ReserveResult excluded(ExclusionReason reason) {
    return new ReserveResult(0L, BigDecimal.ZERO, null, null, reason);
  }
}
