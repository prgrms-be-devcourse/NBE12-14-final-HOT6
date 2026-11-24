package com.back.nbe1214finalhot6.domain.auth.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.auth.dto.PinVerifyRequest;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.auth.service.PinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "간편 비밀번호", description = "PIN 검증 API")
@RestController
@RequestMapping("/api/users/pin")
@RequiredArgsConstructor
public class PinController {

  private final PinService pinService;

  @Operation(
      summary = "PIN 검증",
      description =
          "간편 비밀번호(숫자 6자리)가 맞는지 확인한다. 맞으면 200 이고 실패 횟수가 0이 된다. "
              + "틀리면 400(AUTH-005, 메시지에 남은 횟수), 5번째로 틀리면 잠겨서 409(AUTH-006)이고 "
              + "잠긴 뒤에는 맞는 PIN 도 409 이다. 이용자(USER·OWNER)만 쓸 수 있다")
  @PostMapping("/verify")
  public ApiResponse<Void> verify(
      @AuthenticationPrincipal AuthUser user, @RequestBody @Valid PinVerifyRequest request) {
    pinService.verify(user.userId(), request.pin());
    return ApiResponse.ok(null);
  }
}
