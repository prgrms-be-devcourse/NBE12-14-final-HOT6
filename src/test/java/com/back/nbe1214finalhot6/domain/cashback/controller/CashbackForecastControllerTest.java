package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastAccuracyDay;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastAccuracyResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastAssumptions;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastPoint;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastScenario;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackForecastAccuracyService;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackForecastService;
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

// 운영자로 호출해서 보안 설정을 거쳐도 통과하는지 함께 확인한다.
@WebMvcTest(CashbackForecastController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class CashbackForecastControllerTest {

  private static final String URL = "/api/admin/cashback/forecast";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private CashbackForecastService forecastService;
  @MockitoBean private CashbackForecastAccuracyService accuracyService;

  private ForecastResponse sample() {
    ForecastScenario scenario =
        new ForecastScenario(new BigDecimal("100000.00"), LocalDate.of(2026, 11, 28), null);
    return new ForecastResponse(
        2026,
        11,
        1_000_000L,
        100_000L,
        20L,
        new ForecastAssumptions(
            "RECENT_7D_AVG",
            new BigDecimal("100000.00"),
            new BigDecimal("1.0"),
            new BigDecimal("0.3"),
            "v1",
            LocalDate.of(2026, 11, 18)),
        scenario,
        scenario,
        scenario,
        List.of(new ForecastPoint(LocalDate.of(2026, 11, 19), 870_000L, 900_000L, 930_000L)));
  }

  // 목적: 파라미터를 생략하면 이번 달 계산을 요청하고, 가정·시나리오·예상 선이 공통 응답 형식으로 내려가는지 확인한다.
  @Test
  @DisplayName("파라미터를 생략하면 이번 달 예측을 반환한다")
  void forecast_ok() throws Exception {
    given(forecastService.forecast(null, null)).willReturn(sample());

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.remainingAmount").value(1_000_000))
        .andExpect(jsonPath("$.data.guaranteedUsers").value(20))
        .andExpect(jsonPath("$.data.assumptions.modelVersion").value("v1"))
        .andExpect(jsonPath("$.data.assumptions.demandBasis").value("RECENT_7D_AVG"))
        .andExpect(jsonPath("$.data.mid.exhaustionDate").value("2026-11-28"))
        .andExpect(jsonPath("$.data.fast.noticeLineDate").doesNotExist())
        .andExpect(jsonPath("$.data.points[0].date").value("2026-11-19"))
        .andExpect(jsonPath("$.data.points[0].midRemaining").value(900_000));
  }

  // 목적: 연·월을 주면 그대로 서비스에 전달되는지 확인한다.
  @Test
  @DisplayName("연·월을 서비스에 넘긴다")
  void forecast_withParams() throws Exception {
    given(forecastService.forecast(2026, 11)).willReturn(sample());

    mockMvc
        .perform(get(URL).param("year", "2026").param("month", "11"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.month").value(11));
  }

  // 목적: 서비스가 잘못된 요청을 CSH-007 로 거절하면 400 으로, 숫자가 아닌 파라미터도 400 으로 내려가는지 확인한다.
  @Test
  @DisplayName("잘못된 요청은 400")
  void invalidRequests() throws Exception {
    given(forecastService.forecast(2026, 12))
        .willThrow(new BusinessException(CashbackErrorCode.INVALID_BUDGET, "예측은 이번 달만 계산할 수 있습니다"));

    mockMvc
        .perform(get(URL).param("year", "2026").param("month", "12"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CSH-007"));
    mockMvc.perform(get(URL).param("year", "abc").param("month", "11")).andExpect(status().isBadRequest());
  }

  // 목적: 예측 오차가 평균과 날짜별 값으로 내려가는지 확인한다.
  @Test
  @DisplayName("예측 오차를 반환한다")
  void accuracy_ok() throws Exception {
    given(accuracyService.accuracy())
        .willReturn(
            new ForecastAccuracyResponse(
                20_000L,
                1,
                List.of(
                    new ForecastAccuracyDay(
                        LocalDate.of(2026, 11, 17),
                        new BigDecimal("100000.00"),
                        new BigDecimal("70000.00"),
                        new BigDecimal("130000.00"),
                        120_000L,
                        20_000L,
                        true,
                        "v1"))));

    mockMvc
        .perform(get(URL + "/accuracy"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.averageError").value(20_000))
        .andExpect(jsonPath("$.data.withinRangeDays").value(1))
        .andExpect(jsonPath("$.data.days[0].date").value("2026-11-17"))
        .andExpect(jsonPath("$.data.days[0].actualAmount").value(120_000));
  }

  // 목적: 운영자가 아니면 예측 오차를 볼 수 없는지 확인한다.
  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("이용자는 예측 오차를 볼 수 없다")
  void accuracy_forbidden() throws Exception {
    mockMvc.perform(get(URL + "/accuracy")).andExpect(status().isForbidden());
  }
}
