package com.back.nbe1214finalhot6.domain.payment.controller;

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
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.payment.dto.UserPaymentItem;
import com.back.nbe1214finalhot6.domain.payment.dto.UserPaymentsResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.service.UserPaymentQueryService;
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

/** 이용자 결제 내역 컨트롤러 테스트 (이슈 #215). */
@WebMvcTest(UserPaymentController.class)
@Import(SecurityConfig.class)
@WithAuthUser(role = UserRole.USER)
class UserPaymentControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UserPaymentQueryService queryService;
  @MockitoBean private OwnedResources ownedResources;

  // 로그인한 이용자의 지갑은 5번이다
  @BeforeEach
  void myWalletIsFive() {
    given(ownedResources.walletIdOf(any())).willReturn(5L);
  }

  @Test
  @DisplayName("조회는 200 과 결제 목록(페이지)을 반환한다")
  void 조회성공() throws Exception {
    UserPaymentItem item =
        new UserPaymentItem(
            100L,
            "테스트 카페",
            PaymentStatus.CANCELED,
            30_000L,
            20_000L,
            10_000L,
            500L,
            LocalDateTime.of(2026, 11, 25, 10, 0),
            LocalDateTime.of(2026, 11, 25, 15, 0));
    given(queryService.get(5L, null, 0, 10))
        .willReturn(new UserPaymentsResponse(new PageResponse<>(List.of(item), 0, 10, 1L, 1)));

    mockMvc
        .perform(get("/api/payments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.payments.content[0].paymentId").value(100))
        .andExpect(jsonPath("$.data.payments.content[0].merchantName").value("테스트 카페"))
        .andExpect(jsonPath("$.data.payments.content[0].status").value("CANCELED"))
        .andExpect(jsonPath("$.data.payments.content[0].amount").value(30_000))
        .andExpect(jsonPath("$.data.payments.content[0].cashbackEarnedAmount").value(500))
        .andExpect(jsonPath("$.data.payments.content[0].canceledAt").value("2026-11-25T15:00:00"))
        .andExpect(jsonPath("$.data.payments.totalElements").value(1));
  }

  @Test
  @DisplayName("지갑은 요청이 아니라 로그인한 회원(walletIdOf 결과)으로 정한다")
  void 내_지갑으로_조회() throws Exception {
    mockMvc.perform(get("/api/payments").param("walletId", "99")).andExpect(status().isOk());

    verify(queryService).get(5L, null, 0, 10);
  }

  @Test
  @DisplayName("status, page, size 를 그대로 서비스에 전달한다")
  void 파라미터_전달() throws Exception {
    mockMvc
        .perform(
            get("/api/payments").param("status", "CANCELED").param("page", "2").param("size", "5"))
        .andExpect(status().isOk());

    verify(queryService).get(5L, PaymentStatus.CANCELED, 2, 5);
  }

  @Test
  @DisplayName("처리 중 같은 허용되지 않는 상태는 서비스가 400 (COM-001)으로 거절한다")
  void 잘못된_상태_400() throws Exception {
    given(queryService.get(5L, PaymentStatus.PROCESSING, 0, 10))
        .willThrow(new BusinessException(CommonErrorCode.INVALID_REQUEST));

    mockMvc
        .perform(get("/api/payments").param("status", "PROCESSING"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));
  }

  @Test
  @DisplayName("page 가 음수이거나 size 가 0 이하면 400 이고 서비스는 호출되지 않는다")
  void 파라미터_형식_오류() throws Exception {
    mockMvc.perform(get("/api/payments").param("page", "-1")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/payments").param("size", "0")).andExpect(status().isBadRequest());

    verify(queryService, never()).get(anyLong(), any(), anyInt(), anyInt());
  }

  @Test
  @WithAnonymousUser
  @DisplayName("토큰이 없으면 401 (COM-005)")
  void 토큰없음_401() throws Exception {
    mockMvc
        .perform(get("/api/payments"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COM-005"));

    verify(queryService, never()).get(anyLong(), any(), anyInt(), anyInt());
  }
}
