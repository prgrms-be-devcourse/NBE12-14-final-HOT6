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
 * @param appliedPolicyId 적용한 정책 ID. 달 중 인하 구간이 아니라 월 기본 요율이 적용됐거나 제외면 null
 * @param payableAt 지급 예정 시각. 제외면 null
 * @param exclusionReason 적립 제외 사유. 적립됐으면 {@link CashbackExclusionReason#NONE}
 * @param monthlyLimitRemaining 이번 달 남은 월 적립 한도(원). 이번 결제의 적립액을 뺀 값이고, 제외된 결제도 그 시점의 값을 담는다. 한도를 이미
 *     넘겼으면 0
 */
public record CashbackReserveResponse(
    long earnedAmount,
    BigDecimal appliedRate,
    Long appliedPolicyId,
    LocalDateTime payableAt,
    CashbackExclusionReason exclusionReason,
    long monthlyLimitRemaining) {

  /** 적립 없이 사유만 알리는 결과. */
  public static CashbackReserveResponse excluded(
      CashbackExclusionReason reason, long monthlyLimitRemaining) {
    return new CashbackReserveResponse(
        0L, BigDecimal.ZERO, null, null, reason, monthlyLimitRemaining);
  }
}
