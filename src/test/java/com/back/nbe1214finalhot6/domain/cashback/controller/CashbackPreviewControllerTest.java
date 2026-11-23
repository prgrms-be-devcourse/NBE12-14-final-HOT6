package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackPreviewRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackPreviewResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackService;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantService;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 로그인한 사용자(허용 역할)로 호출해서 보안 설정을 거쳐도 통과하는지 함께 확인한다.
@WebMvcTest(CashbackPreviewController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "USER")
class CashbackPreviewControllerTest {

  private static final String URL = "/api/cashback/preview";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private CashbackService cashbackService;
  @MockitoBean private MerchantService merchantService;

  // 목적: 가맹점의 적립 대상 여부가 서비스 요청에 실리고 미리보기 결과가 공통 응답 형식으로 내려가는지 확인한다.
  @Test
  @DisplayName("가맹점 적립 대상 여부와 함께 미리보기를 계산해 반환한다")
  void preview_ok() throws Exception {
    given(merchantService.isCashbackEligible(7L)).willReturn(true);
    given(cashbackService.preview(new CashbackPreviewRequest(1L, true, 12_000L)))
        .willReturn(
            new CashbackPreviewResponse(
                true, false, new BigDecimal("0.03"), 360L, 4_000L, CashbackExclusionReason.NONE));

    mockMvc
        .perform(
            get(URL).param("walletId", "1").param("merchantId", "7").param("walletUsedAmount", "12000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.earnable").value(true))
        .andExpect(jsonPath("$.data.paused").value(false))
        .andExpect(jsonPath("$.data.appliedRate").value(0.03))
        .andExpect(jsonPath("$.data.expectedAmount").value(360))
        .andExpect(jsonPath("$.data.monthlyLimitRemaining").value(4000))
        .andExpect(jsonPath("$.data.exclusionReason").value("NONE"));
    verify(merchantService).isCashbackEligible(7L);
  }

  // 목적: 필수 파라미터가 빠지거나 금액이 음수·숫자가 아니면 400 으로 걸러지는지 확인한다.
  @Test
  @DisplayName("파라미터가 없거나 잘못되면 400")
  void invalidParameters() throws Exception {
    mockMvc.perform(get(URL)).andExpect(status().isBadRequest());
    mockMvc
        .perform(get(URL).param("walletId", "1").param("merchantId", "7"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get(URL).param("walletId", "1").param("merchantId", "7").param("walletUsedAmount", "-1"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get(URL).param("walletId", "1").param("merchantId", "7").param("walletUsedAmount", "abc"))
        .andExpect(status().isBadRequest());
  }
}
