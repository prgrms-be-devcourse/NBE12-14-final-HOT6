package com.back.nbe1214finalhot6.domain.auth.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.auth.dto.MyInfoResponse;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.auth.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원", description = "내 정보 조회 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserQueryService userQueryService;

  @Operation(
      summary = "내 정보 조회",
      description =
          "로그인한 회원의 이름, 이메일, 역할, PIN 잠금 여부를 돌려준다. USER·OWNER·ADMIN 모두 쓸 수 있다. "
              + "역할은 토큰이 아니라 DB의 현재 값이고, 토큰의 역할과 다르면 needsRelogin 이 true 이다"
              + "(이때는 다시 로그인해야 새 역할이 적용된다). 탈퇴한 회원은 404(COM-009)")
  @GetMapping("/me")
  public ApiResponse<MyInfoResponse> getMyInfo(@AuthenticationPrincipal AuthUser user) {
    return ApiResponse.ok(userQueryService.getMyInfo(user.userId(), user.role()));
  }
}
