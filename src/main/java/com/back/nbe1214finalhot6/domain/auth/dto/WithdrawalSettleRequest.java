package com.back.nbe1214finalhot6.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 탈퇴 실행 요청. 미리보기에서 확인한 환불 금액을 함께 보낸다. */
@Schema(description = "탈퇴 실행 요청")
public record WithdrawalSettleRequest(
    @Schema(
            description = "미리보기에서 확인한 환불 예정액. 실행 시점의 금액이 이와 다르면 409(AUTH-008)로 거절하고 다시 확인하게 한다",
            example = "50000")
        @NotNull
        @PositiveOrZero
        Long expectedRefundAmount) {}
