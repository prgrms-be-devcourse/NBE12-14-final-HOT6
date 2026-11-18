package com.back.nbe1214finalhot6.domain.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupResponse;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 인증 없이(로그인·CSRF 토큰 없이) 호출해서 보안 설정(가입 경로 허용)도 함께 확인
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

  private static final String VALID_BODY =
      """
      {"name": "홍길동", "email": "user@example.com", "password": "Password1!",
       "phone": "01012345678", "pin": "123456"}
      """;

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AuthService authService;

  // 정상 요청이 인증 없이도 201 과 공통 응답 형식으로 내려가고, 해시가 응답에 없는지 확인
  @Test
  @DisplayName("정상 요청이면 인증 없이도 201 과 가입 결과를 반환한다")
  void signup_created() throws Exception {
    given(authService.signup(any()))
        .willReturn(new SignupResponse(1L, "홍길동", "user@example.com", UserRole.USER));

    mockMvc
        .perform(
            post("/api/users/signup").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.name").value("홍길동"))
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data.pinHash").doesNotExist());
  }

  // 회원가입 시 이미 가입된 이메일이 있으면 409 에러 반환
  @Test
  @DisplayName("이메일이 중복이면 409 와 AUTH-001 을 반환한다")
  void duplicated_email() throws Exception {
    given(authService.signup(any()))
        .willThrow(new BusinessException(AuthErrorCode.DUPLICATE_EMAIL));

    mockMvc
        .perform(
            post("/api/users/signup").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("AUTH-001"))
        .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다"));
  }

  // 회원가입 시 이미 가입된 전화번호가 있으면 409 에러 반환
  @Test
  @DisplayName("전화번호가 중복이면 409 와 AUTH-002 을 반환한다")
  void duplicated_phone() throws Exception {
    given(authService.signup(any()))
        .willThrow(new BusinessException(AuthErrorCode.DUPLICATE_PHONE));

    mockMvc
        .perform(
            post("/api/users/signup").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("AUTH-002"))
        .andExpect(jsonPath("$.message").value("이미 사용 중인 전화번호입니다"));
  }

  // 입력 검증 실패 케이스. 모두 400 과 COM-001 로 거절되고 서비스 호출 없음

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @DisplayName("이름이 비어 있으면 400")
  void invalid_name(String name) throws Exception {
    expectBadRequest(
        body(name, "user@example.com", "Password1!", "01012345678", "123456"), "name");
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-an-email", "user@", "@example.com", "   "})
  @DisplayName("이메일 형식이 틀리면 400")
  void invalid_email(String email) throws Exception {
    expectBadRequest(body("홍길동", email, "Password1!", "01012345678", "123456"), "email");
  }

  // 짧음, 특수문자 없음, 숫자 없음, 영문 없음, 공백 포함, 허용되지 않은 특수문자($)
  @ParameterizedTest
  @ValueSource(
      strings = {"Pw1!", "password1", "Password!", "12345678!", "Pass word1!", "Password1$"})
  @DisplayName("비밀번호 규칙을 어기면 400")
  void invalid_password(String password) throws Exception {
    expectBadRequest(
        body("홍길동", "user@example.com", password, "01012345678", "123456"), "password");
  }

  // 하이픈 포함, 02 로 시작, 너무 짧음, 너무 김, 숫자가 아님
  @ParameterizedTest
  @ValueSource(
      strings = {"010-1234-5678", "0212345678", "0101234", "010123456789", "abcdefghijk"})
  @DisplayName("전화번호 형식이 틀리면 400")
  void invalid_phone(String phone) throws Exception {
    expectBadRequest(
        body("홍길동", "user@example.com", "Password1!", phone, "123456"), "phone");
  }

  @ParameterizedTest
  @ValueSource(strings = {"12345", "1234567", "12345a", "   "})
  @DisplayName("PIN 이 6자리 숫자가 아니면 400")
  void invalid_pin(String pin) throws Exception {
    expectBadRequest(
        body("홍길동", "user@example.com", "Password1!", "01012345678", pin), "pin");
  }

  // 길이 제한(이름 100자, 이메일 100자, 비밀번호 15자)을 넘으면 거절되는지 확인
  @Test
  @DisplayName("길이 제한을 넘으면 400 (이름 101자, 이메일 101자, 비밀번호 16자)")
  void invalid_tooLong() throws Exception {
    expectBadRequest(
        body("가".repeat(101), "user@example.com", "Password1!", "01012345678", "123456"), "name");
    expectBadRequest(
        body("홍길동", "a".repeat(95) + "@b.com", "Password1!", "01012345678", "123456"), "email");
    expectBadRequest(
        body("홍길동", "user@example.com", "Password1!Passwo", "01012345678", "123456"),
        "password");
  }

  // 필드가 아예 빠져도(null) @NotBlank 로 걸러지는지 확인
  @Test
  @DisplayName("필수 필드가 빠지면 400")
  void invalid_missingField() throws Exception {
    expectBadRequest(
        """
        {"name": "홍길동", "email": "user@example.com", "password": "Password1!",
         "phone": "01012345678"}
        """,
        "pin");
  }

  // JSON 자체가 깨져 있으면 400 과 COM-001 로 거절되는지 확인
  @Test
  @DisplayName("JSON 형식이 깨져 있으면 400")
  void invalid_malformedJson() throws Exception {
    mockMvc
        .perform(post("/api/users/signup").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("COM-001"));

    verify(authService, never()).signup(any());
  }

  private String body(String name, String email, String password, String phone, String pin) {
    return """
        {"name": "%s", "email": "%s", "password": "%s", "phone": "%s", "pin": "%s"}
        """
        .formatted(name, email, password, phone, pin);
  }

  // 검증 실패는 공통 응답 형식(COM-001)으로 내려가고, 메시지에 실패한 필드명 포함
  private void expectBadRequest(String body, String field) throws Exception {
    mockMvc
        .perform(post("/api/users/signup").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("COM-001"))
        .andExpect(jsonPath("$.message").value(containsString(field + ":")));

    verify(authService, never()).signup(any());
  }
}
