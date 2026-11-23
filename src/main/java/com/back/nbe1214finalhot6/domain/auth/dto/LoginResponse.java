package com.back.nbe1214finalhot6.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답")
public record LoginResponse(
    @Schema(description = "액세스 토큰. API 요청의 Authorization 헤더에 'Bearer 토큰'으로 보낸다")
        String accessToken,
    @Schema(description = "토큰 종류", example = "Bearer") String tokenType,
    @Schema(description = "액세스 토큰 유효 시간(초)", example = "1800") long expiresIn) {

  public static LoginResponse bearer(String accessToken, long expiresInSeconds) {
    return new LoginResponse(accessToken, "Bearer", expiresInSeconds);
  }
}
