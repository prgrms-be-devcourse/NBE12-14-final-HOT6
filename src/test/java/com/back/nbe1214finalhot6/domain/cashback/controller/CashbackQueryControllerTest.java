package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackSummaryResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.EarningResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyLimitResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackQueryService;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 로그인한 사용자(허용 역할)로 호출해서 보안 설정을 거쳐도 통과하는지 함께 확인한다.
@WebMvcTest(CashbackQueryController.class)
@Import(SecurityConfig.class)
@WithAuthUser(role = UserRole.USER) // 허용 역할의 이용자로 로그인한 상태로 호출한다(지갑은 토큰의 회원으로 찾는다)
class CashbackQueryControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private CashbackQueryService queryService;
  @MockitoBean private OwnedResources ownedResources;

  // 로그인한 이용자의 지갑은 7번이다
  @BeforeEach
  void myWalletIsSeven() {
    given(ownedResources.walletIdOf(any())).willReturn(7L);
  }

  // 목적: 월 한도 조회가 200 과 공통 응답 형식으로 한도·사용량·남은 한도를 내려 주는지 확인한다.
  @Test
  @DisplayName("월 한도 조회는 200 과 현황을 반환한다")
  void monthlyLimit_ok() throws Exception {
    given(queryService.getMonthlyLimit(7L))
        .willReturn(new MonthlyLimitResponse(2026, 11, 50_000L, 12_000L, 38_000L));

    mockMvc
        .perform(get("/api/cashback/monthly-limit"))
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
        .perform(get("/api/cashback/earnings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty());

    verify(queryService).listEarnings(7L, null, 0, 20);
  }

  // 목적: 알 수 없는 상태 값이거나, 페이지 번호·크기가 범위를 벗어나면 400 으로 걸러지는지 확인한다.
  @Test
  @DisplayName("적립 내역의 파라미터가 잘못되면 400")
  void invalidParameters() throws Exception {
    mockMvc
        .perform(get("/api/cashback/earnings").param("status", "NOPE"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/cashback/earnings").param("page", "-1"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/cashback/earnings").param("size", "0"))
        .andExpect(status().isBadRequest());
  }

  // 목적: 요청에 다른 회원의 walletId 를 실어 보내도 무시하고 토큰의 회원 지갑으로만 조회하는지 확인한다.
  @Test
  @DisplayName("요청에 다른 지갑 번호를 보내도 토큰의 회원 지갑으로 조회한다")
  void ignoresWalletIdParameter() throws Exception {
    given(queryService.getSummary(7L))
        .willReturn(new CashbackSummaryResponse(2026, 11, 0L, 0L, 0L, 0L));

    mockMvc
        .perform(get("/api/cashback/summary").param("walletId", "99"))
        .andExpect(status().isOk());

    verify(queryService).getSummary(7L);
    verify(queryService, never()).getSummary(99L);
  }

  // 목적: 지갑이 없는 회원이면 404(WAL-001)로 응답하는지 확인한다.
  @Test
  @DisplayName("내 지갑이 없으면 404")
  void walletNotFound() throws Exception {
    given(ownedResources.walletIdOf(any()))
        .willThrow(new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

    mockMvc
        .perform(get("/api/cashback/summary"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WAL-001"));
    mockMvc.perform(get("/api/cashback/monthly-limit")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/cashback/earnings")).andExpect(status().isNotFound());
  }

  // 목적: 요약이 200 과 공통 응답 형식으로 연·월, 적립액, 예정·지급 완료 금액, 제외 건수를 내려 주는지 확인한다.
  @Test
  @DisplayName("이번 달 적립 요약은 200 과 합계를 반환한다")
  void summary_ok() throws Exception {
    given(queryService.getSummary(7L))
        .willReturn(new CashbackSummaryResponse(2026, 11, 900L, 600L, 300L, 2L));

    mockMvc
        .perform(get("/api/cashback/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.year").value(2026))
        .andExpect(jsonPath("$.data.month").value(11))
        .andExpect(jsonPath("$.data.earnedAmount").value(900))
        .andExpect(jsonPath("$.data.pendingAmount").value(600))
        .andExpect(jsonPath("$.data.paidAmount").value(300))
        .andExpect(jsonPath("$.data.excludedCount").value(2));
  }

}
