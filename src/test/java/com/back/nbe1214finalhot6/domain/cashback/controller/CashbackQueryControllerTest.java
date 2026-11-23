package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.dto.EarningResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyLimitResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 인증 없이 호출해서 임시 보안 설정(경로 허용)도 함께 확인한다.
@WebMvcTest(CashbackQueryController.class)
@Import(SecurityConfig.class)
class CashbackQueryControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private CashbackQueryService queryService;

  // 목적: 월 한도 조회가 200 과 공통 응답 형식으로 한도·사용량·남은 한도를 내려 주는지 확인한다.
  @Test
  @DisplayName("월 한도 조회는 200 과 현황을 반환한다")
  void monthlyLimit_ok() throws Exception {
    given(queryService.getMonthlyLimit(7L))
        .willReturn(new MonthlyLimitResponse(2026, 11, 50_000L, 12_000L, 38_000L));

    mockMvc
        .perform(get("/api/cashback/monthly-limit").param("walletId", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.year").value(2026))
        .andExpect(jsonPath("$.data.month").value(11))
        .andExpect(jsonPath("$.data.limit").value(50_000))
        .andExpect(jsonPath("$.data.usedAmount").value(12_000))
        .andExpect(jsonPath("$.data.remaining").value(38_000));
  }

  // 목적: 적립 내역이 페이지 형식으로 나오고 상태·페이지 파라미터가 서비스에 그대로 전달되는지, 제외 사유가 노출되는지 확인한다.
  @Test
  @DisplayName("적립 내역은 상태와 페이지를 서비스에 넘기고 목록을 반환한다")
  void earnings_ok() throws Exception {
    EarningResponse excluded =
        new EarningResponse(
            1L,
            10L,
            EarningStatus.EXCLUDED,
            0L,
            BigDecimal.ZERO,
            LocalDateTime.of(2026, 11, 18, 9, 0),
            null,
            null,
            null,
            CashbackExclusionReason.BUDGET_EXHAUSTED);
    given(queryService.listEarnings(7L, EarningStatus.EXCLUDED, 1, 5))
        .willReturn(new PageResponse<>(List.of(excluded), 1, 5, 6L, 2));

    mockMvc
        .perform(
            get("/api/cashback/earnings")
                .param("walletId", "7")
                .param("status", "EXCLUDED")
                .param("page", "1")
                .param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(6))
        .andExpect(jsonPath("$.data.content[0].status").value("EXCLUDED"))
        .andExpect(jsonPath("$.data.content[0].exclusionReason").value("BUDGET_EXHAUSTED"))
        .andExpect(jsonPath("$.data.content[0].amount").value(0));
  }

  // 목적: 상태·페이지를 생략하면 전체, 첫 페이지, 기본 크기 20 으로 조회하는지 확인한다.
  @Test
  @DisplayName("파라미터를 생략하면 전체 첫 페이지를 기본 크기로 조회한다")
  void earnings_defaults() throws Exception {
    given(queryService.listEarnings(eq(7L), eq(null), eq(0), eq(20)))
        .willReturn(new PageResponse<>(List.of(), 0, 20, 0L, 0));

    mockMvc
        .perform(get("/api/cashback/earnings").param("walletId", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty());

    verify(queryService).listEarnings(7L, null, 0, 20);
  }

  // 목적: 지갑 ID 가 없거나, 알 수 없는 상태 값이거나, 페이지 번호·크기가 범위를 벗어나면 400 으로 걸러지는지 확인한다.
  @Test
  @DisplayName("지갑 ID 가 없거나 파라미터가 잘못되면 400")
  void invalidParameters() throws Exception {
    mockMvc.perform(get("/api/cashback/monthly-limit")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/cashback/earnings")).andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/cashback/earnings").param("walletId", "7").param("status", "NOPE"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/cashback/earnings").param("walletId", "7").param("page", "-1"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/cashback/earnings").param("walletId", "7").param("size", "0"))
        .andExpect(status().isBadRequest());
  }
}
