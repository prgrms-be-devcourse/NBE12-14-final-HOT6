package com.back.nbe1214finalhot6.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/** 탈퇴 실행 요청. 미리보기에서 확인한 환불 금액과 PIN 을 함께 보낸다. PIN 은 로그에 남지 않도록 {@code toString}에서 가린다. */
@Schema(description = "탈퇴 실행 요청")
public record WithdrawalSettleRequest(
    @Schema(
            description = "미리보기에서 확인한 환불 예정액. 실행 시점의 금액이 이와 다르면 409(AUTH-008)로 거절하고 다시 확인하게 한다",
            example = "50000")
        @NotNull
        @PositiveOrZero
        Long expectedRefundAmount,
    @Schema(description = "간편 비밀번호(숫자 6자리). 탈퇴를 실행하기 전에 서버가 검증한다", example = "123456")
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "PIN은 숫자 6자리여야 합니다")
        String pin) {

  @Override
  public String toString() {
    return "WithdrawalSettleRequest[expectedRefundAmount=" + expectedRefundAmount + ", pin=****]";
  }
}
