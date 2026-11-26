package com.back.nbe1214finalhot6.domain.merchant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.dto.AccountResponse;
import com.back.nbe1214finalhot6.domain.wallet.service.AccountQueryService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MerchantAccountController.class)
@Import(SecurityConfig.class)
@WithAuthUser(userId = 7L, role = UserRole.OWNER) // 7번 점주로 로그인한 상태로 호출한다(계좌는 토큰의 회원으로 찾는다)
class MerchantAccountControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private AccountQueryService accountQueryService;
  @MockitoBean private OwnedResources ownedResources;

  @Test
  @DisplayName("로그인한 점주의 모의계좌 잔액을 정산 계좌 잔액으로 반환한다")
  void 조회성공() throws Exception {
    given(ownedResources.merchantIdOf(any())).willReturn(3L);
    given(accountQueryService.getAccount(7L)).willReturn(new AccountResponse(15_000L));

    mockMvc
        .perform(get("/api/merchants/me/account"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.balance").value(15000));
  }

  @Test
  @DisplayName("가맹점이 없는 점주면 404 MER-001 로 응답한다")
  void 가맹점없음() throws Exception {
    given(ownedResources.merchantIdOf(any()))
        .willThrow(new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

    mockMvc
        .perform(get("/api/merchants/me/account"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MER-001"));

    verify(accountQueryService, never()).getAccount(any());
  }
}
