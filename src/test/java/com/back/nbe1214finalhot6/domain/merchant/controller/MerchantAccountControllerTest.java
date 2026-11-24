package com.back.nbe1214finalhot6.domain.merchant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.dto.MerchantAccountResponse;
import com.back.nbe1214finalhot6.domain.wallet.service.MerchantWalletService;
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
@WithAuthUser(role = UserRole.OWNER) // 허용 역할의 점주로 로그인한 상태로 호출한다(번호는 토큰의 회원으로 찾는다)
class MerchantAccountControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private MerchantWalletService walletService;
  @MockitoBean private OwnedResources ownedResources;

  @Test
  @DisplayName("내 가맹점의 정산 계좌 잔액을 반환한다")
  void 조회성공() throws Exception {
    given(ownedResources.merchantIdOf(any())).willReturn(3L);
    given(walletService.getAccount(3L)).willReturn(new MerchantAccountResponse(15_000L));

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
  }
}
