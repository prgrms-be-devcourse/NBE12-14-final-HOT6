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
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import java.util.List;
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

  private MonthlyBudgetResponse monthly(int month) {
    return new MonthlyBudgetResponse(
        2026, month, 1_000_000L, 0L, 0L, 0L, 100_000L, 1_000_000L, BudgetStatus.ACTIVE);
  }

  // 목적: 월 배정 PUT 이 200 과 대비금·상태를 포함한 응답으로 내려가는지 확인한다.
  @Test
  @DisplayName("월 예산 PUT 은 200 과 결과를 반환한다")
  void putMonthly_ok() throws Exception {
    given(budgetService.putMonthly(eq(2026), eq(12), any())).willReturn(monthly(12));

    mockMvc
        .perform(
            put("/api/admin/cashback/budget/monthly/2026/12")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"allocatedAmount": 1000000}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.budgetMonth").value(12))
        .andExpect(jsonPath("$.data.allocatedAmount").value(1_000_000))
        .andExpect(jsonPath("$.data.contingencyAmount").value(100_000))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));
  }

  // 목적: 배정 금액이 빠지면 400 으로 걸러지는지 확인한다.
  @Test
  @DisplayName("배정 금액이 없으면 400")
  void putMonthly_missingAmount() throws Exception {
    mockMvc
        .perform(
            put("/api/admin/cashback/budget/monthly/2026/12")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  // 목적: 합계 초과는 409 와 CSH-005, 사용 중인 달은 409 와 CSH-008 로 변환되는지 확인한다.
  @Test
  @DisplayName("합계 초과는 CSH-005, 사용 중인 달 수정은 CSH-008 (모두 409)")
  void putMonthly_conflicts() throws Exception {
    given(budgetService.putMonthly(eq(2026), eq(11), any()))
        .willThrow(new BusinessException(CashbackErrorCode.BUDGET_ALLOCATION_EXCEEDED));
    given(budgetService.putMonthly(eq(2026), eq(12), any()))
        .willThrow(new BusinessException(CashbackErrorCode.BUDGET_IN_USE));
    String body = "{\"allocatedAmount\": 1000000}";

    mockMvc
        .perform(
            put("/api/admin/cashback/budget/monthly/2026/11")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CSH-005"));
    mockMvc
        .perform(
            put("/api/admin/cashback/budget/monthly/2026/12")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CSH-008"));
  }

  // 목적: 월 조회는 200, 배정하지 않은 달은 404 와 CSH-004 인지 확인한다.
  @Test
  @DisplayName("월 조회는 200, 없는 달은 404 와 CSH-004")
  void getMonthly_okAndNotFound() throws Exception {
    given(budgetService.getMonthly(2026, 12)).willReturn(monthly(12));
    given(budgetService.getMonthly(2026, 1))
        .willThrow(new BusinessException(CashbackErrorCode.BUDGET_NOT_FOUND));

    mockMvc
        .perform(get("/api/admin/cashback/budget/monthly/2026/12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.budgetMonth").value(12));
    mockMvc
        .perform(get("/api/admin/cashback/budget/monthly/2026/1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CSH-004"));
  }

  // 목적: 연도별 목록이 배열로 내려가고, year 파라미터가 없으면 400 인지 확인한다.
  @Test
  @DisplayName("월 예산 목록은 배열로 반환하고 year 가 없으면 400")
  void listMonthly() throws Exception {
    given(budgetService.listMonthly(2026)).willReturn(List.of(monthly(11), monthly(12)));

    mockMvc
        .perform(get("/api/admin/cashback/budget/monthly").param("year", "2026"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].budgetMonth").value(11));
    mockMvc
        .perform(get("/api/admin/cashback/budget/monthly"))
        .andExpect(status().isBadRequest());
  }
}
