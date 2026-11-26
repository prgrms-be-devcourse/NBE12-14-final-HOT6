package com.back.nbe1214finalhot6.domain.qr.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QrLimitController.class)
@Import(SecurityConfig.class)
class QrLimitControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다

  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("결제 한도 조회 시 QR 한 건의 최대 금액을 반환한다")
  void 결제한도조회() throws Exception {
    mockMvc
        .perform(get("/api/qr/limit"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.maxAmount").value(QrCode.MAX_AMOUNT));
  }

  @Test
  @DisplayName("로그인하지 않으면 결제 한도를 조회할 수 없다")
  void 비로그인은거절() throws Exception {
    mockMvc.perform(get("/api/qr/limit")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("운영자는 시민 기능인 결제 한도를 조회할 수 없다")
  void 운영자는거절() throws Exception {
    mockMvc.perform(get("/api/qr/limit")).andExpect(status().isForbidden());
  }
}
