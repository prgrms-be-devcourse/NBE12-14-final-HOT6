package com.back.nbe1214finalhot6.domain.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;


public record PaymentRequest(
    @Schema(hidden = true)
    Long walletId,
    @NotBlank @Size(max = 64)
    String qrToken,
    @PositiveOrZero
    Long cashbackUsed
)

{
    public PaymentRequest {
        cashbackUsed = cashbackUsed == null ? 0L : cashbackUsed;
    }
}
