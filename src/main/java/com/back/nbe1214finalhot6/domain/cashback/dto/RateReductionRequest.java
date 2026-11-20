package com.back.nbe1214finalhot6.domain.cashback.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 요율 인하 요청.
 *
 * @param rate 새 요율. 소수 비율(3% = 0.03)이며 적용 시각의 현재 요율보다 낮아야 한다
 * @param effectiveAt 적용 시각. 현재 분 + 예고 일수 이후이고, 이 시각이 속한 달 안에서만 적용한다
 */
public record RateReductionRequest(@NotNull BigDecimal rate, @NotNull LocalDateTime effectiveAt) {}
