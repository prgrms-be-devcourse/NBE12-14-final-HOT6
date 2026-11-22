package com.back.nbe1214finalhot6.domain.payment.dto;

import jakarta.validation.constraints.*;


public record PaymentRequest(
    @NotNull
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
