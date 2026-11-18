package com.back.nbe1214finalhot6.domain.cashback.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 정책 등록 요청.
 *
 * @param rate 요율. 소수 비율(3% = 0.03)
 * @param startsAt 적용 시작 시각(포함)
 * @param endsAt 적용 종료 시각(미포함). 생략하면 무기한
 */
public record PolicyCreateRequest(
    @NotNull BigDecimal rate, @NotNull LocalDateTime startsAt, LocalDateTime endsAt) {}
