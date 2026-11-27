package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 아직 적용되지 않은 인하 예약. 수정 화면이 지금 값을 채우는 데 쓴다.
 *
 * @param reason 마지막으로 남긴 인하 사유. 남긴 적이 없으면 null
 * @param notice 이 인하를 알린 공지. 없으면 null
 */
public record RateReservationResponse(
    Long policyId,
    BigDecimal rate,
    LocalDateTime effectiveAt,
    String reason,
    RateNoticeRequest notice) {}
