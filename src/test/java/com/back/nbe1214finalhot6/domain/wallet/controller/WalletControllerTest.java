package com.back.nbe1214finalhot6.domain.wallet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.wallet.dto.WalletResponse;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.service.ChargeResult;
import com.back.nbe1214finalhot6.domain.wallet.service.ChargeService;
import com.back.nbe1214finalhot6.domain.wallet.service.RefundResult;
import com.back.nbe1214finalhot6.domain.wallet.service.RefundService;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletQueryService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/** 지갑 컨트롤러. 요청·응답 형태, 멱등키·금액 검증, 지갑을 토큰의 회원으로 찾는지, 실패 코드 전달을 확인한다. */
@WebMvcTest(WalletController.class)
@Import(SecurityConfig.class)
@WithAuthUser(userId = 5L, role = UserRole.USER) // 허용 역할의 회원(5번)으로 로그인한 상태로 호출한다
class WalletControllerTest {

  private static final long MY_WALLET = 7L;
  private static final String CHARGES = "/api/wallet/charges";
  private static final String REFUNDS = "/api/wallet/refunds";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private WalletQueryService walletQueryService;
  @MockitoBean private ChargeService chargeService;
  @MockitoBean private RefundService refundService;
  @MockitoBean private OwnedResources ownedResources;

  // 로그인한 회원의 지갑은 7번이다
  @BeforeEach
  void myWalletIsSeven() {
    given(ownedResources.walletIdOf(any())).willReturn(MY_WALLET);
  }

  // 목적: 지갑 조회가 로그인한 회원 번호로 서비스를 부르고, 응답 필드가 그대로 내려가는지 확인한다.
  @Test
  @DisplayName("내 지갑 조회는 로그인한 회원의 지갑 정보를 돌려준다")
  void 지갑조회() throws Exception {
    given(walletQueryService.getMyWallet(5L))
        .willReturn(new WalletResponse(150_000, -200, 0, 2_000_000, 1_000_000, 600_000, 400_000));

    mockMvc
        .perform(get("/api/wallet"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.devMoney").value(150_000))
        .andExpect(jsonPath("$.data.cashbackBalance").value(-200))
        .andExpect(jsonPath("$.data.availableCashback").value(0))
        .andExpect(jsonPath("$.data.holdingLimit").value(2_000_000))
        .andExpect(jsonPath("$.data.monthlyChargeLimit").value(1_000_000))
        .andExpect(jsonPath("$.data.chargedThisMonth").value(600_000))
        .andExpect(jsonPath("$.data.chargeableAmount").value(400_000));
  }

  // 목적: 충전이 요청이 아니라 토큰의 회원 지갑(7번)으로 처리되는지 확인한다.
  // 요청에 남의 walletId(999)를 보내도 서비스에는 내 지갑만 넘어가야 남의 지갑에 충전할 수 없다.
  @Test
  @DisplayName("충전은 요청의 walletId 가 아니라 로그인한 회원의 지갑으로 처리한다")
  void 충전은토큰의지갑으로() throws Exception {
    String key = UUID.randomUUID().toString();
    given(chargeService.charge(MY_WALLET, 100_000L, key))
        .willReturn(new ChargeResult(11L, 100_000L, LocalDateTime.of(2026, 11, 23, 10, 0)));

    mockMvc
        .perform(post(CHARGES).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\": 100000, \"walletId\": 999}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.chargeId").value(11))
        .andExpect(jsonPath("$.data.amount").value(100_000))
        .andExpect(jsonPath("$.data.createdAt").value("2026-11-23T10:00:00"));

    verify(chargeService).charge(MY_WALLET, 100_000L, key);
    verify(chargeService, never()).charge(eq(999L), anyLong(), anyString());
  }

  @Test
  @DisplayName("환불도 요청의 walletId 가 아니라 로그인한 회원의 지갑으로 처리한다")
  void 환불은토큰의지갑으로() throws Exception {
    String key = UUID.randomUUID().toString();
    given(refundService.refund(MY_WALLET, 50_000L, key))
        .willReturn(new RefundResult(21L, 50_000L, LocalDateTime.of(2026, 11, 23, 11, 0)));

    mockMvc
        .perform(post(REFUNDS).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\": 50000, \"walletId\": 999}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.refundId").value(21))
        .andExpect(jsonPath("$.data.amount").value(50_000));

    verify(refundService).refund(MY_WALLET, 50_000L, key);
    verify(refundService, never()).refund(eq(999L), anyLong(), anyString());
  }

  // 목적: 멱등키가 없으면 COM-002 로 거절하고, 서비스는 부르지 않는지 확인한다.
  @ParameterizedTest
  @ValueSource(strings = {CHARGES, REFUNDS})
  @DisplayName("Idempotency-Key 헤더가 없으면 400 COM-002 이다")
  void 멱등키없음(String path) throws Exception {
    mockMvc
        .perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 1000}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-002"));

    verifyNoMoneyMoved();
  }

  @ParameterizedTest
  @ValueSource(strings = {CHARGES, REFUNDS})
  @DisplayName("Idempotency-Key 가 UUID 형식이 아니면 400 COM-001 이다")
  void 멱등키형식오류(String path) throws Exception {
    mockMvc
        .perform(
            post(path).header("Idempotency-Key", "1-1-1-1-1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 1000}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));

    verifyNoMoneyMoved();
  }

  // 목적: 금액이 없거나 0 이하이거나 숫자가 아니면 서비스를 부르기 전에 400 COM-001 로 거절하는지 확인한다.
  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"amount\": 0}", "{\"amount\": -1000}", "{\"amount\": \"만원\"}"})
  @DisplayName("금액이 잘못되면 충전·환불 모두 400 COM-001 이고 서비스를 부르지 않는다")
  void 금액오류(String body) throws Exception {
    mockMvc.perform(request(CHARGES, body)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));
    mockMvc.perform(request(REFUNDS, body)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));

    verifyNoMoneyMoved();
  }

  // 목적: 한도 초과·잔액 부족처럼 서비스가 거절한 결과가 코드 그대로 응답되는지 확인한다.
  @Test
  @DisplayName("월 충전 한도를 넘으면 409 WAL-007, 잔액이 부족하면 환불은 409 WAL-004 이다")
  void 서비스실패코드전달() throws Exception {
    given(chargeService.charge(anyLong(), anyLong(), anyString()))
        .willThrow(new BusinessException(WalletErrorCode.MONTHLY_CHARGE_LIMIT_EXCEEDED));
    given(refundService.refund(anyLong(), anyLong(), anyString()))
        .willThrow(new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE));

    mockMvc.perform(request(CHARGES, "{\"amount\": 1000}")).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WAL-007"));
    mockMvc.perform(request(REFUNDS, "{\"amount\": 1000}")).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WAL-004"));
  }

  // 목적: 내 지갑이 없으면(공용 도구가 WAL-001) 404 로 응답하고 충전·환불을 시도하지 않는지 확인한다.
  @Test
  @DisplayName("내 지갑이 없으면 404 WAL-001 이고 충전·환불을 시도하지 않는다")
  void 내지갑없음() throws Exception {
    given(ownedResources.walletIdOf(any()))
        .willThrow(new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

    mockMvc.perform(request(CHARGES, "{\"amount\": 1000}")).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WAL-001"));
    mockMvc.perform(request(REFUNDS, "{\"amount\": 1000}")).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WAL-001"));

    verifyNoMoneyMoved();
  }

  // 목적: 로그인하지 않으면 401, 운영자(ADMIN)는 시민 기능이라 403 인지 보안 설정을 함께 확인한다.
  @Test
  @WithAnonymousUser
  @DisplayName("로그인하지 않으면 401 이다")
  void 비로그인() throws Exception {
    mockMvc.perform(get("/api/wallet")).andExpect(status().isUnauthorized());
    mockMvc.perform(request(CHARGES, "{\"amount\": 1000}")).andExpect(status().isUnauthorized());
    mockMvc.perform(request(REFUNDS, "{\"amount\": 1000}")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithAuthUser(role = UserRole.ADMIN)
  @DisplayName("운영자는 지갑 기능을 쓸 수 없다(403)")
  void 운영자는403() throws Exception {
    mockMvc.perform(get("/api/wallet")).andExpect(status().isForbidden());
    mockMvc.perform(request(CHARGES, "{\"amount\": 1000}")).andExpect(status().isForbidden());
    mockMvc.perform(request(REFUNDS, "{\"amount\": 1000}")).andExpect(status().isForbidden());

    verifyNoMoneyMoved();
  }

  private RequestBuilder request(String path, String body) {
    return post(path)
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private void verifyNoMoneyMoved() {
    verify(chargeService, never()).charge(anyLong(), anyLong(), anyString());
    verify(refundService, never()).refund(anyLong(), anyLong(), anyString());
  }
}
