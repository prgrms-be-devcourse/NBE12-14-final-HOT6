package com.back.nbe1214finalhot6.domain.wallet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.wallet.dto.WalletTransactionResponse;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletTransactionService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 지갑 거래 내역 컨트롤러 테스트 (이슈 #169). */
@WebMvcTest(WalletTransactionController.class)
@Import(SecurityConfig.class)
@WithAuthUser(userId = 1L) // 로그인한 회원(AuthUser)으로 호출한다. 내 지갑은 setUp 에서 1번으로 정한다
class WalletTransactionControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private OwnedResources ownedResources;
  @MockitoBean private WalletTransactionService walletTransactionService;

  @BeforeEach
  void setUp() {
    // 로그인한 회원의 지갑은 1번이다
    given(ownedResources.walletIdOf(any())).willReturn(1L);
  }

  @Test
  @DisplayName("거래 내역 조회는 200 과 페이지 응답(content, page, size, totalElements, totalPages)을 반환한다")
  void 조회성공_응답필드() throws Exception {
    // given : 결제 거래 한 건이 든 첫 페이지
    WalletTransactionResponse row =
        new WalletTransactionResponse(
            100L,
            WalletTransactionType.PAYMENT_DEVMONEY,
            -7_000L, // 차감은 음수
            3_000L, // 거래 후 잔액
            false, // 데브머니 잔액을 바꾼 거래
            55L, // refKey (결제 ID)
            55L, // paymentId
            LocalDateTime.of(2026, 11, 18, 9, 0));
    given(walletTransactionService.getTransactions(1L, 0, 10))
        .willReturn(new PageResponse<>(List.of(row), 0, 10, 1L, 1));

    // when & then
    mockMvc
        .perform(get("/api/wallet/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].transactionId").value(100))
        .andExpect(jsonPath("$.data.content[0].type").value("PAYMENT_DEVMONEY"))
        .andExpect(jsonPath("$.data.content[0].amount").value(-7_000))
        .andExpect(jsonPath("$.data.content[0].balanceAfter").value(3_000))
        .andExpect(jsonPath("$.data.content[0].cashback").value(false))
        .andExpect(jsonPath("$.data.content[0].refKey").value(55))
        .andExpect(jsonPath("$.data.content[0].paymentId").value(55))
        .andExpect(jsonPath("$.data.content[0].createdAt").value("2026-11-18T09:00:00"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(10))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1));
  }

  @Test
  @DisplayName("지갑 번호는 요청이 아니라 로그인한 회원의 지갑(walletIdOf 결과)으로 조회한다")
  void 내_지갑으로_조회() throws Exception {
    // when : 남의 지갑 번호(99)를 쿼리로 보내 본다
    mockMvc.perform(get("/api/wallet/transactions").param("walletId", "99")).andExpect(status().isOk());

    // then : 99 가 아니라 내 지갑 1 로 조회했다
    verify(walletTransactionService).getTransactions(1L, 0, 10);
  }

  @Test
  @DisplayName("page, size 를 보내면 그대로 전달하고, 안 보내면 page=0, size=10 이다")
  void 페이지_파라미터() throws Exception {
    mockMvc
        .perform(get("/api/wallet/transactions").param("page", "2").param("size", "5"))
        .andExpect(status().isOk());
    verify(walletTransactionService).getTransactions(1L, 2, 5);

    mockMvc.perform(get("/api/wallet/transactions")).andExpect(status().isOk());
    verify(walletTransactionService).getTransactions(1L, 0, 10);
  }

  @Test
  @DisplayName("page 가 음수이거나 size 가 0 이하이면 400")
  void 파라미터_범위_오류() throws Exception {
    mockMvc
        .perform(get("/api/wallet/transactions").param("page", "-1"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/wallet/transactions").param("size", "0"))
        .andExpect(status().isBadRequest());

    // 검증에 걸리면 서비스는 호출되지 않는다
    verify(walletTransactionService, never()).getTransactions(anyLong(), anyInt(), anyInt());
  }

  @Test
  @DisplayName("지갑이 없으면 404 (WAL-001)")
  void 지갑없음_404() throws Exception {
    // given : 내 지갑이 없다
    given(ownedResources.walletIdOf(any()))
        .willThrow(new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

    // when & then
    mockMvc
        .perform(get("/api/wallet/transactions"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("WAL-001"));
  }

  @Test
  @WithAnonymousUser
  @DisplayName("토큰이 없으면 401 (COM-005)")
  void 토큰없음_401() throws Exception {
    mockMvc
        .perform(get("/api/wallet/transactions"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COM-005"));
  }
}
