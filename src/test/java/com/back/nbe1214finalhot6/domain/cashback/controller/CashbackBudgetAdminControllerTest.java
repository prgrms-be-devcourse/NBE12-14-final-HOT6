package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 인증 없이(로그인·CSRF 토큰 없이) 호출해서 임시 보안 설정도 함께 확인한다.
@WebMvcTest(CashbackBudgetAdminController.class)
@Import(SecurityConfig.class)
class CashbackBudgetAdminControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private CashbackBudgetService budgetService;

  private AnnualBudgetResponse sample() {
    return new AnnualBudgetResponse(2026, 1_000_000L, 0L, 0L, 1_000_000L);
  }

  // 목적: PUT 이 200 과 공통 응답 형식으로 내려가고 금액 필드가 그대로 나오는지 확인한다.
  @Test
  @DisplayName("연 예산 PUT 은 200 과 결과를 반환한다")
  void putAnnual_ok() throws Exception {
    given(budgetService.putAnnual(eq(2026), any())).willReturn(sample());

    mockMvc
        .perform(
            put("/api/admin/cashback/budget/annual/2026")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"totalAmount": 1000000}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.budgetYear").value(2026))
        .andExpect(jsonPath("$.data.totalAmount").value(1_000_000))
        .andExpect(jsonPath("$.data.remainingAmount").value(1_000_000));
  }

  // 목적: 총액이 빠지면 400 으로 걸러지는지 확인한다.
  @Test
  @DisplayName("총액이 없으면 400")
  void putAnnual_missingTotal() throws Exception {
    mockMvc
        .perform(
            put("/api/admin/cashback/budget/annual/2026")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  // 목적: 연도 자리에 숫자가 아닌 값이 오면 400 으로 처리되는지 확인한다.
  @Test
  @DisplayName("연도가 숫자가 아니면 400")
  void putAnnual_invalidYearPath() throws Exception {
    mockMvc
        .perform(
            put("/api/admin/cashback/budget/annual/abc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"totalAmount": 1000000}
                    """))
        .andExpect(status().isBadRequest());
  }

  // 목적: 서비스의 금액 오류가 400 과 CSH-007 로 변환되는지 확인한다.
  @Test
  @DisplayName("총액이 올바르지 않으면 400 과 CSH-007")
  void putAnnual_invalidBudget() throws Exception {
    given(budgetService.putAnnual(eq(2026), any()))
        .willThrow(new BusinessException(CashbackErrorCode.INVALID_BUDGET));

    mockMvc
        .perform(
            put("/api/admin/cashback/budget/annual/2026")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"totalAmount": 0}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CSH-007"));
  }

  // 목적: GET 이 200 으로 내려가고, 없는 연도는 404 와 CSH-004 로 변환되는지 확인한다.
  @Test
  @DisplayName("조회는 200, 없는 연도는 404 와 CSH-004")
  void getAnnual_okAndNotFound() throws Exception {
    given(budgetService.getAnnual(2026)).willReturn(sample());
    given(budgetService.getAnnual(2030))
        .willThrow(new BusinessException(CashbackErrorCode.BUDGET_NOT_FOUND));

    mockMvc
        .perform(get("/api/admin/cashback/budget/annual/2026"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.budgetYear").value(2026));

    mockMvc
        .perform(get("/api/admin/cashback/budget/annual/2030"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CSH-004"));
  }
}
