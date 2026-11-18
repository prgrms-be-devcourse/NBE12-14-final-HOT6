package com.back.nbe1214finalhot6.domain.auth.dto;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 응답")
public record SignupResponse(
    @Schema(description = "회원 ID", example = "1") Long id,
    @Schema(description = "회원 이름", example = "홍길동") String name,
    @Schema(description = "회원 이메일", example = "user@example.com") String email,
    @Schema(description = "회원 역할", example = "USER") UserRole role) {

  public static SignupResponse from(User user) {
    return new SignupResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
  }
}
