package com.back.nbe1214finalhot6.domain.wallet.controller;

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
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.wallet.dto.AccountResponse;
import com.back.nbe1214finalhot6.domain.wallet.dto.AccountTransactionResponse;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.service.AccountQueryService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
@WithAuthUser(userId = 7L) // 로그인한 회원(AuthUser)으로 호출한다
class AccountControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private AccountQueryService accountQueryService;

  @Test
  @DisplayName("잔액 조회는 토큰의 회원으로 서비스를 부르고 balance 를 반환한다")
  void 잔액_조회() throws Exception {
    given(accountQueryService.getAccount(7L)).willReturn(new AccountResponse(1_850_000L));

    mockMvc
        .perform(get("/api/wallet/account"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.balance").value(1_850_000));
  }

  @Test
  @DisplayName("거래 내역은 페이지 응답과 거래 필드(종류, 부호 있는 금액, 거래 후 잔액, 참조 ID, 시각)를 반환한다")
  void 내역_응답필드() throws Exception {
    AccountTransactionResponse row =
        new AccountTransactionResponse(
            100L, AccountTransactionType.CHARGE, -100_000L, 1_900_000L, 55L, LocalDateTime.of(2026, 11, 24, 9, 0));
    given(accountQueryService.getTransactions(7L, 0, 10))
        .willReturn(new PageResponse<>(List.of(row), 0, 10, 1, 1));

    mockMvc
        .perform(get("/api/wallet/account/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].transactionId").value(100))
        .andExpect(jsonPath("$.data.content[0].type").value("CHARGE"))
        .andExpect(jsonPath("$.data.content[0].amount").value(-100_000))
        .andExpect(jsonPath("$.data.content[0].balanceAfter").value(1_900_000))
        .andExpect(jsonPath("$.data.content[0].refKey").value(55))
        .andExpect(jsonPath("$.data.content[0].createdAt").exists())
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(10))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1));
  }

  @Test
  @DisplayName("page, size 를 넘기면 그대로 서비스에 전달하고, 안 넘기면 0, 10 이다")
  void 페이지_전달() throws Exception {
    given(accountQueryService.getTransactions(anyLong(), anyInt(), anyInt()))
        .willReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

    mockMvc.perform(get("/api/wallet/account/transactions")).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/wallet/account/transactions").param("page", "3").param("size", "25"))
        .andExpect(status().isOk());

    verify(accountQueryService).getTransactions(7L, 0, 10);
    verify(accountQueryService).getTransactions(7L, 3, 25);
  }

  // 목적: 요청에 남의 회원·계좌 번호를 넣어도 무시하고 토큰의 회원(7)으로만 조회하는지 확인한다.
  @Test
  @DisplayName("요청에 남의 userId·accountId 를 넣어도 무시하고 토큰의 회원으로 조회한다")
  void 남의_번호_무시() throws Exception {
    given(accountQueryService.getTransactions(anyLong(), anyInt(), anyInt()))
        .willReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));
    given(accountQueryService.getAccount(anyLong())).willReturn(new AccountResponse(1L));

    mockMvc
        .perform(get("/api/wallet/account/transactions").param("userId", "99").param("accountId", "99"))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/wallet/account").param("userId", "99").param("accountId", "99"))
        .andExpect(status().isOk());

    verify(accountQueryService).getTransactions(7L, 0, 10);
    verify(accountQueryService).getAccount(7L);
    verify(accountQueryService, never()).getAccount(99L);
    verify(accountQueryService, never()).getTransactions(99L, 0, 10);
  }

  @Test
  @DisplayName("page 가 음수이거나 size 가 0 이하이면 400 이고 서비스를 부르지 않는다")
  void 잘못된_페이지() throws Exception {
    mockMvc
        .perform(get("/api/wallet/account/transactions").param("page", "-1"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/wallet/account/transactions").param("size", "0"))
        .andExpect(status().isBadRequest());

    verify(accountQueryService, never()).getTransactions(anyLong(), anyInt(), anyInt());
  }

  @Test
  @DisplayName("계좌가 없으면 서비스의 404(WAL-008)가 그대로 내려간다")
  void 계좌_없음() throws Exception {
    given(accountQueryService.getAccount(7L))
        .willThrow(new BusinessException(WalletErrorCode.ACCOUNT_NOT_FOUND));

    mockMvc
        .perform(get("/api/wallet/account"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WAL-008"));
  }

  @Test
  @WithAuthUser(userId = 1L, role = UserRole.ADMIN)
  @DisplayName("운영자는 시민 기능이라 403(COM-008)이다")
  void 운영자_403() throws Exception {
    mockMvc
        .perform(get("/api/wallet/account"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COM-008"));
    mockMvc.perform(get("/api/wallet/account/transactions")).andExpect(status().isForbidden());
  }

  @Test
  @WithAnonymousUser
  @DisplayName("토큰이 없으면 401(COM-005)이다")
  void 익명_401() throws Exception {
    mockMvc
        .perform(get("/api/wallet/account"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COM-005"));
    mockMvc.perform(get("/api/wallet/account/transactions")).andExpect(status().isUnauthorized());
  }
}
