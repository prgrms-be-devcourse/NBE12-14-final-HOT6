package com.back.nbe1214finalhot6.domain.auth.dto;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

/** 내 정보 조회 응답. 비밀번호·PIN 해시·실패 횟수 같은 민감한 값은 넣지 않는다. */
@Schema(description = "내 정보 조회 응답")
public record MyInfoResponse(
    @Schema(description = "이름", example = "홍길동") String name,
    @Schema(description = "이메일(로그인 ID)", example = "hong@devpay.com") String email,
    @Schema(
            description =
                "DB의 현재 역할. 토큰의 역할과 다르면(가맹점 등록으로 점주가 된 직후 등) 다시 로그인해야 새 역할이 적용된다",
            example = "USER")
        UserRole role,
    @Schema(description = "PIN 잠금 여부. 5회 실패로 잠겼으면 true", example = "false") boolean pinLocked,
    @Schema(
            description =
                "토큰의 역할이 DB의 현재 역할과 다르면 true. true 이면 다시 로그인해야 새 역할이 적용된다(가맹점을 등록해 점주가 된 직후 등)",
            example = "false")
        boolean needsRelogin) {

  /** tokenRole 은 지금 쓰는 토큰에 들어 있는 역할이다. DB의 현재 역할과 다르면 다시 로그인해야 한다. */
  public static MyInfoResponse from(User user, UserRole tokenRole) {
    return new MyInfoResponse(
        user.getName(),
        user.getEmail(),
        user.getRole(),
        user.getPinLockedAt() != null,
        user.getRole() != tokenRole);
  }
}
