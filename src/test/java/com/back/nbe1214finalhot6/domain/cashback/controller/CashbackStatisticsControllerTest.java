package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.cashback.dto.DailyEarningResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.DailyEarningTrendResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyLimitUsageResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackStatisticsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CashbackStatisticsController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class CashbackStatisticsControllerTest {

  private static final String URL = "/api/admin/cashback/statistics";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private CashbackStatisticsService statisticsService;

  // 목적: 두 통계가 공통 응답 형식으로 내려가는지 확인한다.
  @Test
  @DisplayName("통계를 반환한다")
  void statistics_ok() throws Exception {
    given(statisticsService.monthlyLimitUsage())
        .willReturn(new MonthlyLimitUsageResponse(2026, 11, 50_000L, 3, 1, 0, 0, 0, 2));
    given(statisticsService.dailyEarnings())
        .willReturn(
            new DailyEarningTrendResponse(
                List.of(
                    new DailyEarningResponse(
                        LocalDate.of(2026, 11, 18), 3_000L, 2_000L, new BigDecimal("0.1")))));

    mockMvc
        .perform(get(URL + "/monthly-limit"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userCount").value(3))
        .andExpect(jsonPath("$.data.reached").value(2));
    mockMvc
        .perform(get(URL + "/daily-earnings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.days[0].date").value("2026-11-18"))
        .andExpect(jsonPath("$.data.days[0].amount").value(3_000))
        .andExpect(jsonPath("$.data.days[0].rate").value(0.1));
  }

  // 목적: 운영자가 아니면 볼 수 없는지 확인한다.
  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("이용자는 통계를 볼 수 없다")
  void statistics_forbidden() throws Exception {
    mockMvc.perform(get(URL + "/monthly-limit")).andExpect(status().isForbidden());
    mockMvc.perform(get(URL + "/daily-earnings")).andExpect(status().isForbidden());
  }
}
