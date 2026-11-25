package com.back.nbe1214finalhot6.domain.qr.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "결제 한도")
public record QrLimitResponse(
    @Schema(description = "QR 한 건으로 결제할 수 있는 최대 금액(원)", example = "1000000") long maxAmount) {}
