package com.back.nbe1214finalhot6.domain.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(
    // TODO: JWT 연동 후 토큰에서 추출하도록 변경
    @NotNull Long walletId,
    @NotNull Long merchantId,
    @NotNull Long qrId,
    @NotNull
    @Positive Long amount) {

}
