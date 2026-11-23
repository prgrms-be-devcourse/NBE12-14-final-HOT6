package com.back.nbe1214finalhot6.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** PIN 검증 요청. PIN 은 로그에 남지 않도록 {@code toString}에서 가린다. */
@Schema(description = "PIN 검증 요청")
public record PinVerifyRequest(
    @Schema(description = "간편 비밀번호(숫자 6자리)", example = "123456")
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "PIN은 숫자 6자리여야 합니다")
        String pin) {

  @Override
  public String toString() {
    return "PinVerifyRequest[pin=****]";
  }
}
