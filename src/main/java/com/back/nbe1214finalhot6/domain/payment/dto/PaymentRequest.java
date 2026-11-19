package com.back.nbe1214finalhot6.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentRequest(
    // TODO: JWT 연동 후 토큰에서 추출하도록 변경
    @NotNull Long walletId,
    @NotNull Long merchantId,
    // 이용자가 스캔한 QR 의 토큰(UUID). QR 이미지에 담긴 값을 그대로 보낸다
    @NotBlank @Size(max = 64) String qrToken,
    @NotNull
    @Positive Long amount) {

}
