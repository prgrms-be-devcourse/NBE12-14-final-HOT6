package com.back.nbe1214finalhot6.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.auth.dto.MyInfoResponse;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.service.UserQueryService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 내 정보 조회 컨트롤러. 응답 형태, 토큰의 회원으로 조회하는지, 역할별 접근을 확인한다. */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@WithAuthUser(userId = 5L, role = UserRole.USER) // 허용 역할의 회원(5번)으로 로그인한 상태로 호출한다
class UserControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private UserQueryService userQueryService;

  // 목적: 이름·이메일·역할·PIN 잠금 여부가 그대로 내려가고, 민감한 값은 응답에 없는지 확인한다.
  @Test
  @DisplayName("내 정보 조회는 이름, 이메일, 역할, PIN 잠금 여부를 돌려주고 민감한 값은 없다")
  void 내정보조회() throws Exception {
    given(userQueryService.getMyInfo(5L, UserRole.USER))
        .willReturn(new MyInfoResponse("홍길동", "hong@devpay.com", UserRole.USER, false, false));

    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("홍길동"))
        .andExpect(jsonPath("$.data.email").value("hong@devpay.com"))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.pinLocked").value(false))
        .andExpect(jsonPath("$.data.needsRelogin").value(false))
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data.pinHash").doesNotExist())
        .andExpect(jsonPath("$.data.pinFailCount").doesNotExist());
  }

  // 목적: 조회할 회원을 요청이 아니라 토큰의 회원(5번)으로 정하는지 확인한다.
  // 요청에 남의 회원 번호(999)를 붙여도 서비스에는 내 번호만 넘어가야 남의 정보를 볼 수 없다.
  @Test
  @DisplayName("요청에 남의 회원 번호를 붙여도 로그인한 회원의 정보만 조회한다")
  void 토큰의회원으로조회() throws Exception {
    given(userQueryService.getMyInfo(5L, UserRole.USER))
        .willReturn(new MyInfoResponse("홍길동", "hong@devpay.com", UserRole.USER, false, false));

    mockMvc.perform(get("/api/users/me").param("userId", "999")).andExpect(status().isOk());

    verify(userQueryService).getMyInfo(5L, UserRole.USER);
    verify(userQueryService, never()).getMyInfo(eq(999L), any());
  }

  // 목적: DB 의 역할이 바뀐 직후(토큰은 USER, DB는 OWNER)에는 needsRelogin 이 true 로 내려가고, PIN 잠금도 그대로 내려가는지 확인한다.
  @Test
  @DisplayName("토큰은 USER 인데 DB 역할이 OWNER 이면 needsRelogin 이 true 이다")
  void 역할변경직후() throws Exception {
    given(userQueryService.getMyInfo(5L, UserRole.USER))
        .willReturn(new MyInfoResponse("김점주", "owner@devpay.com", UserRole.OWNER, true, true));

    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("OWNER"))
        .andExpect(jsonPath("$.data.pinLocked").value(true))
        .andExpect(jsonPath("$.data.needsRelogin").value(true));
  }

  // 목적: 탈퇴한 회원은 서비스가 거절한 404(COM-009)가 그대로 응답되는지 확인한다.
  @Test
  @DisplayName("탈퇴한 회원은 404 COM-009 이다")
  void 탈퇴한회원() throws Exception {
    given(userQueryService.getMyInfo(anyLong(), any()))
        .willThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("COM-009"));
  }

  // 목적: 내 정보 조회는 "공통"이라 운영자(ADMIN)도 쓸 수 있는지, 로그인하지 않으면 401 인지 확인한다.
  @Test
  @WithAuthUser(userId = 1L, role = UserRole.ADMIN)
  @DisplayName("운영자도 내 정보를 조회할 수 있다")
  void 운영자도조회() throws Exception {
    given(userQueryService.getMyInfo(1L, UserRole.ADMIN))
        .willReturn(new MyInfoResponse("관리자", "admin@devpay.com", UserRole.ADMIN, false, false));

    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("ADMIN"));
  }

  @Test
  @WithAnonymousUser
  @DisplayName("로그인하지 않으면 401 이다")
  void 비로그인() throws Exception {
    mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());

    verify(userQueryService, never()).getMyInfo(anyLong(), any());
  }
}
