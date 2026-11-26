package com.back.nbe1214finalhot6.domain.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 내 모의계좌 잔액. */
@Schema(description = "내 모의계좌 잔액")
public record AccountResponse(
    @Schema(description = "모의계좌 잔액(원). 가입하면 500만원이고, 충전하면 줄고 환불하면 늘어남", example = "4850000")
        long balance) {}
