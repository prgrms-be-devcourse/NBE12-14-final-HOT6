package com.back.nbe1214finalhot6.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

@Schema(description = "로그인 요청")
public record LoginRequest(
    @Schema(description = "가입한 이메일", example = "user@example.com") @NotBlank @Size(max = 100)
        String email,
    // 가입 규칙(길이·문자 구성)은 검사하지 않는다. 규칙을 알려주지 않고 일치 여부만 본다.
    @Schema(description = "비밀번호", example = "Password1!") @NotBlank @Size(max = 100)
        String password) {

  // 가입 때와 같은 방식으로 이메일 앞뒤 공백 제거, 소문자 변환
  public LoginRequest {
    email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
  }
}
