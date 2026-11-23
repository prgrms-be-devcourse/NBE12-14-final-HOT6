package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetWarning;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetStateService;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 인증 없이(로그인·CSRF 토큰 없이) 호출해서 임시 보안 설정도 함께 확인한다.
@WebMvcTest(CashbackBudgetStopController.class)
@Import(SecurityConfig.class)
class CashbackBudgetStopControllerTest {

  private static final String BASE = "/api/admin/cashback/budget/monthly/2026/11";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private CashbackBudgetStateService stateService;
  @MockitoBean private CashbackBudgetService budgetService;

  private MonthlyBudgetResponse monthly(BudgetStatus status) {
    return new MonthlyBudgetResponse(
        2026,
        11,
        1_000_000L,
        0L,
        0L,
        0L,
        0L,
        100_000L,
        1_000_000L,
        BigDecimal.ZERO,
        BigDecimal.ONE,
        BudgetWarning.NONE,
        new BigDecimal("0.0300"),
        status);
  }

  // 목적: 공지 후 중단이 200 과 바뀐 월 예산(공지 상태)을 내려 주는지 확인한다.
  @Test
  @DisplayName("공지 후 중단은 200 과 바뀐 월 예산을 반환한다")
  void noticeStop_ok() throws Exception {
    given(budgetService.getMonthly(2026, 11)).willReturn(monthly(BudgetStatus.NOTICED));

    mockMvc
        .perform(post(BASE + "/notice-stop"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("NOTICED"));
  }

  // 목적: 긴급 중단이 확인 값(confirm: true)이 있을 때만 200 이고, 없거나 false 거나 본문이 비어 있으면 400 인지 확인한다.
  @Test
  @DisplayName("긴급 중단은 confirm 이 true 일 때만 실행한다")
  void emergencyStop_requiresConfirm() throws Exception {
    given(budgetService.getMonthly(2026, 11)).willReturn(monthly(BudgetStatus.STOPPED));

    mockMvc
        .perform(post(BASE + "/emergency-stop").contentType(MediaType.APPLICATION_JSON).content("{\"confirm\": true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("STOPPED"));
    for (String body : new String[] {"{}", "{\"confirm\": false}", "{\"confirm\": null}"}) {
      mockMvc
          .perform(post(BASE + "/emergency-stop").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest());
    }
    mockMvc.perform(post(BASE + "/emergency-stop")).andExpect(status().isBadRequest());
  }

  // 목적: 이미 중단·마감된 달은 409 와 CSH-012, 월 예산이 없으면 404 와 CSH-004 로 내려가는지 확인한다.
  @Test
  @DisplayName("이미 중단된 달은 409 와 CSH-012, 월 예산이 없으면 404 와 CSH-004")
  void businessErrors() throws Exception {
    doThrow(new BusinessException(CashbackErrorCode.BUDGET_ALREADY_STOPPED))
        .when(stateService)
        .noticeStopManually(2026, 11);
    doThrow(new BusinessException(CashbackErrorCode.BUDGET_NOT_FOUND))
        .when(stateService)
        .emergencyStopManually(2026, 11);

    mockMvc
        .perform(post(BASE + "/notice-stop"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CSH-012"));
    mockMvc
        .perform(post(BASE + "/emergency-stop").contentType(MediaType.APPLICATION_JSON).content("{\"confirm\": true}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CSH-004"));
  }
}
