package com.back.nbe1214finalhot6.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.service.PinService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** PIN 검증 컨트롤러. 요청 검증, 토큰의 회원으로 검증하는지, 실패 코드 전달, 역할별 접근을 확인한다. */
@WebMvcTest(PinController.class)
@Import(SecurityConfig.class)
@WithAuthUser(userId = 5L, role = UserRole.USER) // 허용 역할의 회원(5번)으로 로그인한 상태로 호출한다
class PinControllerTest {

  private static final String VERIFY = "/api/users/pin/verify";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private PinService pinService;

  // 목적: 검증할 회원을 요청이 아니라 토큰의 회원(5번)으로 정하는지 확인한다.
  // 요청에 남의 회원 번호(999)를 붙여도 서비스에는 내 번호만 넘어가야 남의 PIN 을 시도할 수 없다.
  @Test
  @DisplayName("PIN 검증은 요청의 회원 번호가 아니라 로그인한 회원으로 한다")
  void 토큰의회원으로검증() throws Exception {
    mockMvc
        .perform(request("{\"pin\": \"123456\", \"userId\": 999}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(pinService).verify(5L, "123456");
    verify(pinService, never()).verify(org.mockito.ArgumentMatchers.eq(999L), anyString());
  }

  // 목적: 틀렸을 때 서비스가 거절한 코드와 남은 횟수 메시지가 그대로 응답되는지 확인한다.
  @Test
  @DisplayName("PIN 이 틀리면 400 AUTH-005 와 남은 횟수 메시지이다")
  void 불일치() throws Exception {
    willThrow(
            new BusinessException(
                AuthErrorCode.PIN_MISMATCH, "PIN이 일치하지 않습니다 (잔여 3회)"))
        .given(pinService)
        .verify(5L, "000000");

    mockMvc
        .perform(request("{\"pin\": \"000000\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH-005"))
        .andExpect(jsonPath("$.message").value("PIN이 일치하지 않습니다 (잔여 3회)"));
  }

  @Test
  @DisplayName("PIN 이 잠겼으면 409 AUTH-006 이다(401 이 아니다)")
  void 잠금() throws Exception {
    willThrow(new BusinessException(AuthErrorCode.PIN_LOCKED)).given(pinService).verify(anyLong(), anyString());

    mockMvc
        .perform(request("{\"pin\": \"123456\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH-006"));
  }

  // 목적: 6자리 숫자가 아니면 서비스를 부르기 전에 400 COM-001 로 거절하는지 확인한다(PIN 시도로 세지 않는다).
  @ParameterizedTest
  @ValueSource(
      strings = {"{}", "{\"pin\": \"\"}", "{\"pin\": \"12345\"}", "{\"pin\": \"1234567\"}", "{\"pin\": \"12345a\"}", "{\"pin\": \"12 456\"}"})
  @DisplayName("6자리 숫자가 아닌 PIN 은 400 COM-001 이고 서비스를 부르지 않는다")
  void 형식오류(String body) throws Exception {
    mockMvc
        .perform(request(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));

    verify(pinService, never()).verify(anyLong(), anyString());
  }

  @Test
  @DisplayName("탈퇴한 회원은 404 COM-009 이다")
  void 탈퇴() throws Exception {
    willThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND))
        .given(pinService)
        .verify(anyLong(), anyString());

    mockMvc
        .perform(request("{\"pin\": \"123456\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("COM-009"));
  }

  // 목적: 이용자뿐 아니라 점주(OWNER)도 PIN 을 검증하고, 운영자(ADMIN)는 못 쓰며, 로그인하지 않으면 401 인지 확인한다.
  @Test
  @WithAuthUser(userId = 6L, role = UserRole.OWNER)
  @DisplayName("점주도 PIN 을 검증할 수 있다")
  void 점주도가능() throws Exception {
    mockMvc.perform(request("{\"pin\": \"123456\"}")).andExpect(status().isOk());

    verify(pinService).verify(6L, "123456");
  }

  @Test
  @WithAuthUser(userId = 1L, role = UserRole.ADMIN)
  @DisplayName("운영자는 PIN 을 검증할 수 없다(403)")
  void 운영자는403() throws Exception {
    mockMvc.perform(request("{\"pin\": \"123456\"}")).andExpect(status().isForbidden());

    verify(pinService, never()).verify(anyLong(), anyString());
  }

  @Test
  @WithAnonymousUser
  @DisplayName("로그인하지 않으면 401 이다")
  void 비로그인() throws Exception {
    mockMvc.perform(request("{\"pin\": \"123456\"}")).andExpect(status().isUnauthorized());

    verify(pinService, never()).verify(anyLong(), anyString());
  }

  private MockHttpServletRequestBuilder request(String body) {
    return post(VERIFY).contentType(MediaType.APPLICATION_JSON).content(body);
  }
}
