package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackPreviewRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackPreviewResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackService;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantService;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 로그인한 이용자(허용 역할)로 호출해서 보안 설정을 거쳐도 통과하는지 함께 확인한다. 지갑은 토큰의 회원으로 찾는다.
@WebMvcTest(CashbackPreviewController.class)
@Import(SecurityConfig.class)
@WithAuthUser(role = UserRole.USER)
class CashbackPreviewControllerTest {

  private static final String URL = "/api/cashback/preview";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private CashbackService cashbackService;
  @MockitoBean private MerchantService merchantService;
  @MockitoBean private OwnedResources ownedResources;

  // 로그인한 이용자의 지갑은 1번이다
  @BeforeEach
  void myWalletIsOne() {
    given(ownedResources.walletIdOf(any())).willReturn(1L);
  }

  // 목적: 가맹점의 적립 대상 여부가 서비스 요청에 실리고 미리보기 결과가 공통 응답 형식으로 내려가는지 확인한다.
  @Test
  @DisplayName("가맹점 적립 대상 여부와 함께 미리보기를 계산해 반환한다")
  void preview_ok() throws Exception {
    given(merchantService.isCashbackEligible(7L)).willReturn(true);
    given(cashbackService.preview(new CashbackPreviewRequest(1L, true, 12_000L)))
        .willReturn(
            new CashbackPreviewResponse(
                true, false, new BigDecimal("0.03"), 360L, 4_000L, CashbackExclusionReason.NONE, false));

    mockMvc
        .perform(get(URL).param("merchantId", "7").param("walletUsedAmount", "12000"))
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

  // 목적: 로그인한 이용자가 그 가맹점의 점주 본인이면 본인 결제 여부가 서비스 요청에 실려 SELF_PAYMENT 로 안내되는지 확인한다(#301).
  @Test
  @DisplayName("본인 가맹점이면 본인 결제로 요청해 SELF_PAYMENT 를 반환한다")
  void preview_selfPayment() throws Exception {
    given(merchantService.isCashbackEligible(7L)).willReturn(true);
    given(merchantService.isOwnedBySamePerson(eq(7L), any())).willReturn(true);
    given(cashbackService.preview(new CashbackPreviewRequest(1L, true, 12_000L, true)))
        .willReturn(
            new CashbackPreviewResponse(
                false, false, BigDecimal.ZERO, 0L, 4_000L, CashbackExclusionReason.SELF_PAYMENT, false));

    mockMvc
        .perform(get(URL).param("merchantId", "7").param("walletUsedAmount", "12000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.earnable").value(false))
        .andExpect(jsonPath("$.data.exclusionReason").value("SELF_PAYMENT"));
  }

  // 목적: 필수 파라미터가 빠지거나 금액이 음수·숫자가 아니면 400 으로 걸러지는지 확인한다.
  @Test
  @DisplayName("파라미터가 없거나 잘못되면 400")
  void invalidParameters() throws Exception {
    mockMvc.perform(get(URL)).andExpect(status().isBadRequest());
    mockMvc.perform(get(URL).param("merchantId", "7")).andExpect(status().isBadRequest());
    mockMvc
        .perform(get(URL).param("merchantId", "7").param("walletUsedAmount", "-1"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get(URL).param("merchantId", "7").param("walletUsedAmount", "abc"))
        .andExpect(status().isBadRequest());
  }

  // 목적: 요청에 다른 회원의 walletId 를 실어 보내도 무시하고 토큰의 회원 지갑으로 계산하는지 확인한다.
  @Test
  @DisplayName("요청에 다른 지갑 번호를 보내도 토큰의 회원 지갑으로 계산한다")
  void ignoresWalletIdParameter() throws Exception {
    given(merchantService.isCashbackEligible(7L)).willReturn(true);
    given(cashbackService.preview(new CashbackPreviewRequest(1L, true, 12_000L)))
        .willReturn(
            new CashbackPreviewResponse(
                true, false, new BigDecimal("0.03"), 360L, 4_000L, CashbackExclusionReason.NONE, false));

    mockMvc
        .perform(
            get(URL)
                .param("walletId", "99")
                .param("merchantId", "7")
                .param("walletUsedAmount", "12000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.expectedAmount").value(360));
  }

  // 목적: 지갑이 없는 회원이면 404(WAL-001)로 응답하는지 확인한다.
  @Test
  @DisplayName("내 지갑이 없으면 404")
  void walletNotFound() throws Exception {
    given(merchantService.isCashbackEligible(7L)).willReturn(true);
    given(ownedResources.walletIdOf(any()))
        .willThrow(new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

    mockMvc
        .perform(get(URL).param("merchantId", "7").param("walletUsedAmount", "12000"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WAL-001"));
  }
}
