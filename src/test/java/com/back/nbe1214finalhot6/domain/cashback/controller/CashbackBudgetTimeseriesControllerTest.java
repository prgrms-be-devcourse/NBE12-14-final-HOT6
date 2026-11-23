package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetMonthSeries;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetTimePoint;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetTimeseriesService;
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

// 로그인한 사용자(허용 역할)로 호출해서 보안 설정을 거쳐도 통과하는지 함께 확인한다.
@WebMvcTest(CashbackBudgetTimeseriesController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class CashbackBudgetTimeseriesControllerTest {

  private static final String URL = "/api/admin/cashback/budget/timeseries";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private CashbackBudgetTimeseriesService timeseriesService;

  // 목적: 기간 파라미터가 서비스에 날짜로 전달되고 월별 시리즈와 점(지급·확보·남은 예산)이 공통 응답 형식으로 내려가는지 확인한다.
  @Test
  @DisplayName("기간을 넘겨 월별 시리즈를 반환한다")
  void timeseries_ok() throws Exception {
    BudgetMonthSeries series =
        new BudgetMonthSeries(
            2026,
            11,
            1_000_000L,
            100_000L,
            List.of(new BudgetTimePoint(LocalDate.of(2026, 11, 3), 300L, 600L, 999_100L)));
    given(timeseriesService.getTimeseries(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 18)))
        .willReturn(List.of(series));

    mockMvc
        .perform(get(URL).param("from", "2026-11-01").param("to", "2026-11-18"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].year").value(2026))
        .andExpect(jsonPath("$.data[0].allocatedAmount").value(1_000_000))
        .andExpect(jsonPath("$.data[0].contingencyAmount").value(100_000))
        .andExpect(jsonPath("$.data[0].points[0].date").value("2026-11-03"))
        .andExpect(jsonPath("$.data[0].points[0].paidAmount").value(300))
        .andExpect(jsonPath("$.data[0].points[0].reservedAmount").value(600))
        .andExpect(jsonPath("$.data[0].points[0].remainingAmount").value(999_100));
  }

  // 목적: 기간 파라미터가 없거나 날짜 형식이 아니면 400 으로 걸러지는지 확인한다.
  @Test
  @DisplayName("기간 파라미터가 없거나 잘못되면 400")
  void invalidParameters() throws Exception {
    mockMvc.perform(get(URL)).andExpect(status().isBadRequest());
    mockMvc.perform(get(URL).param("from", "2026-11-01")).andExpect(status().isBadRequest());
    mockMvc
        .perform(get(URL).param("from", "abc").param("to", "2026-11-18"))
        .andExpect(status().isBadRequest());
  }

  // 목적: 잘못된 기간(서비스가 CSH-007 로 거절)이 400 과 CSH-007 로 내려가는지 확인한다.
  @Test
  @DisplayName("잘못된 기간은 400 과 CSH-007")
  void invalidRange() throws Exception {
    given(timeseriesService.getTimeseries(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 1)))
        .willThrow(new BusinessException(CashbackErrorCode.INVALID_BUDGET));

    mockMvc
        .perform(get(URL).param("from", "2026-11-18").param("to", "2026-11-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CSH-007"));
  }
}
