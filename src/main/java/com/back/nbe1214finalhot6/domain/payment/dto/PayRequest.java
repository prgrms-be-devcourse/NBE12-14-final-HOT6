package com.back.nbe1214finalhot6.domain.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 결제 요청 본문(API). 결제할 지갑은 요청이 아니라 로그인한 회원(토큰)으로 정하므로 지갑 번호는 받지 않는다.
 *
 * <p>PIN 은 결제를 시작하기 전에 서버가 검증한다. 로그에 남지 않도록 {@code toString}에서 가린다. 결제 서비스 안에서 쓰는 요청은 {@link PaymentRequest}이고, 여기서는
 * PIN 을 뺀 값으로 만들어 넘긴다.
 */
@Schema(description = "QR 결제 요청")
public record PayRequest(
    @Schema(description = "스캔한 QR 의 토큰", example = "qr-token-1") @NotBlank @Size(max = 64)
        String qrToken,
    @Schema(description = "결제에 쓸 캐시백(원). 없으면 0", example = "1000") @PositiveOrZero Long cashbackUsed,
    @Schema(description = "간편 비밀번호(숫자 6자리). 결제 전에 서버가 검증한다", example = "123456")
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "PIN은 숫자 6자리여야 합니다")
        String pin) {

  public PayRequest {
    cashbackUsed = cashbackUsed == null ? 0L : cashbackUsed;
  }

  @Override
  public String toString() {
    return "PayRequest[qrToken=" + qrToken + ", cashbackUsed=" + cashbackUsed + ", pin=****]";
  }
}
