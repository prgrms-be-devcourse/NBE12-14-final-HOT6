package com.back.nbe1214finalhot6.domain.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 환불 요청. 지갑은 요청이 아니라 로그인한 회원(토큰)으로 정한다. */
@Schema(description = "지갑 환불 요청")
public record RefundRequest(
    @Schema(description = "환불할 금액(원). 0보다 커야 하고 데브머니 잔액 이하", example = "50000") @NotNull @Positive Long amount) {}
