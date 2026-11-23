package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.math.BigDecimal;

/**
 * 결제 전 캐시백 미리보기 결과. "예상" 값이며 결제가 확정될 때 달라질 수 있고, 확정 결과는 {@code reserve} 응답과 영수증이 기준이다.
 *
 * @param earnable 지금 조건으로 적립받을 수 있는지(제외 사유가 없는지)
 * @param paused 이번 달 적립이 중단됐는지(월 예산이 중단·마감 상태). 공지 상태는 적립이 계속되므로 false
 * @param appliedRate 적용 요율. 적립받을 수 없으면 0
 * @param expectedAmount 예상 적립액(원). 적립받을 수 없으면 0
 * @param monthlyLimitRemaining 이번 달 남은 월 적립 한도(원). 이번 결제를 반영하기 전 값이고, 한도를 이미 넘겼으면 0
 * @param exclusionReason 적립받을 수 없는 사유. 받을 수 있으면 {@link CashbackExclusionReason#NONE}
 */
public record CashbackPreviewResponse(
    boolean earnable,
    boolean paused,
    BigDecimal appliedRate,
    long expectedAmount,
    long monthlyLimitRemaining,
    CashbackExclusionReason exclusionReason) {}
