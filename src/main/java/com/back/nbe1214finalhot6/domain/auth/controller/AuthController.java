package com.back.nbe1214finalhot6.domain.auth.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.auth.dto.LoginRequest;
import com.back.nbe1214finalhot6.domain.auth.dto.LoginResponse;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupRequest;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupResponse;
import com.back.nbe1214finalhot6.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증·회원", description = "회원가입·로그인 관련 API") // Swagger 그룹 이름
@RestController
@RequestMapping("/api") // 공통 경로
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @Operation(summary = "회원가입", description = "이메일, 전화번호 중복을 확인하고 회원을 등록")
  @PostMapping("/users/signup")
  public ResponseEntity<ApiResponse<SignupResponse>> signup(
      @Valid @RequestBody SignupRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(authService.signup(request)));
  }

  @Operation(
      summary = "점주 가입",
      description =
          "점주(OWNER) 계정과 잔액 0원인 개인 모의계좌를 만든다. 지갑은 만들지 않는다. "
              + "입력 규칙은 회원가입과 같고, 이메일·전화번호는 점주·운영자 계정과만 겹칠 수 없다")
  @PostMapping("/owners/signup")
  public ResponseEntity<ApiResponse<SignupResponse>> signupOwner(
      @Valid @RequestBody SignupRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(authService.signupOwner(request)));
  }

  @Operation(
      summary = "로그인",
      description = "이메일·비밀번호로 로그인하고 액세스 토큰을 발급. 실패 사유(이메일 없음·비밀번호 불일치·탈퇴)는 구분하지 않음")
  @PostMapping("/auth/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
  }
}
