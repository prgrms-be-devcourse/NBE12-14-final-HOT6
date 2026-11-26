package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({CashbackRulesController.class, CashbackRulesAdminController.class})
@Import({SecurityConfig.class, CashbackRulesControllerTest.PropertiesConfig.class})
class CashbackRulesControllerTest {

  @TestConfiguration
  static class PropertiesConfig {
    @Bean
    CashbackProperties cashbackProperties() {
      return new CashbackProperties(
          new BigDecimal("0.10"), new BigDecimal("0.5"), 4, 5, 0, 1, null, null, null);
    }
  }

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다

  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("이용자는 지급 지연 일수만 조회한다")
  void 이용자지급규칙() throws Exception {
    mockMvc
        .perform(get("/api/cashback/rules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.payoutDelayDays").value(5))
        .andExpect(jsonPath("$.data.contingencyRate").doesNotExist())
        .andExpect(jsonPath("$.data.noticeDays").doesNotExist());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("운영자는 이용자용 지급 규칙을 조회할 수 없다")
  void 운영자는이용자규칙거절() throws Exception {
    mockMvc.perform(get("/api/cashback/rules")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("로그인하지 않으면 이용자용 규칙을 조회할 수 없다")
  void 비로그인은이용자규칙거절() throws Exception {
    mockMvc.perform(get("/api/cashback/rules")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("운영자는 대비금·긴급선 비율과 공지·지급 일수를 조회한다")
  void 운영자규칙() throws Exception {
    mockMvc
        .perform(get("/api/admin/cashback/rules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.contingencyRate").value(0.10))
        .andExpect(jsonPath("$.data.emergencyRatio").value(0.5))
        .andExpect(jsonPath("$.data.noticeDays").value(4))
        .andExpect(jsonPath("$.data.payoutDelayDays").value(5));
  }

  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("이용자는 운영자용 규칙을 조회할 수 없다")
  void 이용자는운영자규칙거절() throws Exception {
    mockMvc.perform(get("/api/admin/cashback/rules")).andExpect(status().isForbidden());
  }
}
