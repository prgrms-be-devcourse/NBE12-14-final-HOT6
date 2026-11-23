package com.back.nbe1214finalhot6.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.dto.LoginRequest;
import com.back.nbe1214finalhot6.domain.auth.dto.LoginResponse;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 인증 없이 호출해서 보안 설정(로그인 경로 허용)도 함께 확인
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthLoginControllerTest {

  private static final String VALID_BODY =
      """
      {"email": "user@example.com", "password": "Password1!"}
      """;

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AuthService authService;

  @Test
  @DisplayName("로그인하면 인증 없이도 200 과 토큰을 공통 응답 형식으로 반환한다")
  void login_ok() throws Exception {
    given(authService.login(any())).willReturn(LoginResponse.bearer("access-token", 1800L));

    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access-token"))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.expiresIn").value(1800));
  }

  @Test
  @DisplayName("로그인에 실패하면 401 과 AUTH-003 을 반환한다")
  void login_failed() throws Exception {
    given(authService.login(any())).willThrow(new BusinessException(AuthErrorCode.LOGIN_FAILED));

    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value(AuthErrorCode.LOGIN_FAILED.getMessage()));
  }

  @Test
  @DisplayName("비밀번호가 맞은 탈퇴 계정은 401 과 AUTH-004 로 안내한다")
  void login_withdrawn() throws Exception {
    given(authService.login(any())).willThrow(new BusinessException(AuthErrorCode.WITHDRAWN_USER));

    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-004"))
        .andExpect(jsonPath("$.message").value("탈퇴한 계정입니다. 다시 이용하려면 관리자에게 문의해 주세요."));
  }

  @Test
  @DisplayName("이메일에 공백과 대문자가 있어도 정규화되어 서비스에 전달된다")
  void login_normalizesEmail() throws Exception {
    given(authService.login(any())).willReturn(LoginResponse.bearer("t", 1800L));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \" User@Example.COM \", \"password\": \"Password1!\"}"))
        .andExpect(status().isOk());

    ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
    verify(authService).login(captor.capture());
    assertThat(captor.getValue().email()).isEqualTo("user@example.com");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"email\": \"\", \"password\": \"Password1!\"}",
        "{\"email\": \"   \", \"password\": \"Password1!\"}",
        "{\"password\": \"Password1!\"}",
        "{\"email\": \"user@example.com\", \"password\": \"\"}",
        "{\"email\": \"user@example.com\"}",
        "{}",
        "{"
      })
  @DisplayName("이메일이나 비밀번호가 비었거나 JSON 이 깨져 있으면 400 과 COM-001 이다")
  void login_invalid(String body) throws Exception {
    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));

    verify(authService, never()).login(any());
  }

  @Test
  @DisplayName("길이 제한(이메일·비밀번호 100자)을 넘으면 400 이다")
  void login_tooLong() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\": \"user@example.com\", \"password\": \"" + "a".repeat(101) + "\"}"))
        .andExpect(status().isBadRequest());

    verify(authService, never()).login(any());
  }
}
