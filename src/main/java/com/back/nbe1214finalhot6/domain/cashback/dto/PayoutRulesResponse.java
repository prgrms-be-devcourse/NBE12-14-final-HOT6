package com.back.nbe1214finalhot6.domain.cashback.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이용자가 보는 적립 지급 규칙")
public record PayoutRulesResponse(
    @Schema(description = "지급 지연 일수. 결제 다음 날 00:00 + N일에 지급한다", example = "3") int payoutDelayDays) {}
