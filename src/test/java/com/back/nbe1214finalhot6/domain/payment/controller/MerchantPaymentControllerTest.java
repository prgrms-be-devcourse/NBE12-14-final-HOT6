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
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.payment.dto.MerchantPaymentItem;
import com.back.nbe1214finalhot6.domain.payment.dto.MerchantPaymentsResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.exception.PaymentErrorCode;
import com.back.nbe1214finalhot6.domain.payment.service.MerchantPaymentQueryService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.time.LocalDate;
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

/** 사장님 결제 내역 컨트롤러 테스트 (PAY-04, 이슈 #201). */
@WebMvcTest(MerchantPaymentController.class)
@Import(SecurityConfig.class)
@WithAuthUser(role = UserRole.OWNER) // 허용 역할의 점주로 로그인한 상태로 호출한다(번호는 토큰의 회원으로 찾는다)
class MerchantPaymentControllerTest {

  private static final LocalDate FROM = LocalDate.of(2026, 11, 18);
  private static final LocalDate TO = LocalDate.of(2026, 11, 20);

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private MerchantPaymentQueryService queryService;
  @MockitoBean private OwnedResources ownedResources;

  // 로그인한 점주의 가맹점은 3번이다
  @BeforeEach
  void myMerchantIsThree() {
    given(ownedResources.merchantIdOf(any())).willReturn(3L);
  }

  @Test
  @DisplayName("조회는 200 과 기간 요약, 결제 목록(페이지)을 반환한다")
  void 조회성공() throws Exception {
    MerchantPaymentItem item =
        new MerchantPaymentItem(
            100L,
            PaymentStatus.CANCELED,
            30_000L,
            20_000L,
            10_000L,
            LocalDateTime.of(2026, 11, 18, 10, 0),
            LocalDateTime.of(2026, 11, 18, 15, 0));
    given(queryService.get(3L, FROM, TO, 0, 20))
        .willReturn(
            new MerchantPaymentsResponse(
                FROM, TO, 5L, 150_000L, 1L, 30_000L, 120_000L,
                new PageResponse<>(List.of(item), 0, 20, 1L, 1)));

    mockMvc
        .perform(get("/api/merchants/me/payments").param("from", "2026-11-18").param("to", "2026-11-20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.from").value("2026-11-18"))
        .andExpect(jsonPath("$.data.to").value("2026-11-20"))
        .andExpect(jsonPath("$.data.paymentCount").value(5))
        .andExpect(jsonPath("$.data.paymentAmount").value(150_000))
        .andExpect(jsonPath("$.data.cancelCount").value(1))
        .andExpect(jsonPath("$.data.cancelAmount").value(30_000))
        .andExpect(jsonPath("$.data.netSales").value(120_000))
        .andExpect(jsonPath("$.data.payments.content[0].paymentId").value(100))
        .andExpect(jsonPath("$.data.payments.content[0].status").value("CANCELED"))
        .andExpect(jsonPath("$.data.payments.content[0].amount").value(30_000))
        .andExpect(jsonPath("$.data.payments.content[0].walletUsedAmount").value(20_000))
        .andExpect(jsonPath("$.data.payments.content[0].cashbackUsedAmount").value(10_000))
        .andExpect(jsonPath("$.data.payments.content[0].completedAt").value("2026-11-18T10:00:00"))
        .andExpect(jsonPath("$.data.payments.content[0].canceledAt").value("2026-11-18T15:00:00"))
        // 결제한 이용자의 정보는 응답에 없다
        .andExpect(jsonPath("$.data.payments.content[0].walletId").doesNotExist())
        .andExpect(jsonPath("$.data.payments.totalElements").value(1));
  }

  @Test
  @DisplayName("가맹점 번호는 요청이 아니라 로그인한 점주의 가맹점(merchantIdOf 결과)으로 조회한다")
  void 내_가맹점으로_조회() throws Exception {
    // when : 남의 가맹점 번호(99)를 쿼리로 보내 본다
    mockMvc
        .perform(get("/api/merchants/me/payments").param("merchantId", "99"))
        .andExpect(status().isOk());

    // then : 99 가 아니라 내 가맹점 3 으로 조회했다 (기간은 생략해서 null, 기본 페이지 0, 크기 20)
    verify(queryService).get(3L, null, null, 0, 20);
  }

  @Test
  @DisplayName("기간과 page, size 를 그대로 서비스에 전달한다")
  void 파라미터_전달() throws Exception {
    mockMvc
        .perform(
            get("/api/merchants/me/payments")
                .param("from", "2026-11-18")
                .param("to", "2026-11-20")
                .param("page", "2")
                .param("size", "5"))
        .andExpect(status().isOk());

    verify(queryService).get(3L, FROM, TO, 2, 5);
  }

  @Test
  @DisplayName("기간이 올바르지 않으면 400 (PAY-008)")
  void 기간_오류() throws Exception {
    given(queryService.get(3L, TO, FROM, 0, 20))
        .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_INVALID_PERIOD));

    mockMvc
        .perform(get("/api/merchants/me/payments").param("from", "2026-11-20").param("to", "2026-11-18"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PAY-008"));
  }

  @Test
  @DisplayName("날짜 형식이 틀리거나 page 가 음수, size 가 0 이하이면 400 이고 서비스는 호출되지 않는다")
  void 파라미터_형식_오류() throws Exception {
    mockMvc.perform(get("/api/merchants/me/payments").param("from", "2026/11/18")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/merchants/me/payments").param("page", "-1")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/merchants/me/payments").param("size", "0")).andExpect(status().isBadRequest());

    verify(queryService, never()).get(anyLong(), any(), any(), anyInt(), anyInt());
  }

  @Test
  @DisplayName("내 가맹점이 없으면 404 (MER-001)")
  void 가맹점없음_404() throws Exception {
    given(ownedResources.merchantIdOf(any()))
        .willThrow(new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

    mockMvc
        .perform(get("/api/merchants/me/payments"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MER-001"));
  }

  @Test
  @WithAuthUser(role = UserRole.USER)
  @DisplayName("점주가 아닌 이용자(USER)가 호출하면 403 (COM-008)")
  void 이용자_403() throws Exception {
    mockMvc
        .perform(get("/api/merchants/me/payments"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COM-008"));

    verify(queryService, never()).get(anyLong(), any(), any(), anyInt(), anyInt());
  }

  @Test
  @WithAnonymousUser
  @DisplayName("토큰이 없으면 401 (COM-005)")
  void 토큰없음_401() throws Exception {
    mockMvc
        .perform(get("/api/merchants/me/payments"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COM-005"));
  }

  @Test
  @DisplayName("한 건 조회는 200 과 결제 정보를 반환하고 결제한 이용자의 정보는 담지 않는다")
  void 한건_조회성공() throws Exception {
    MerchantPaymentItem item =
        new MerchantPaymentItem(
            34L,
            PaymentStatus.CANCELED,
            850_000L,
            850_000L,
            0L,
            LocalDateTime.of(2026, 11, 25, 14, 30),
            LocalDateTime.of(2026, 11, 25, 14, 35));
    given(queryService.getOne(3L, 34L)).willReturn(item);

    mockMvc
        .perform(get("/api/merchants/me/payments/34"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.paymentId").value(34))
        .andExpect(jsonPath("$.data.status").value("CANCELED"))
        .andExpect(jsonPath("$.data.amount").value(850_000))
        .andExpect(jsonPath("$.data.walletUsedAmount").value(850_000))
        .andExpect(jsonPath("$.data.cashbackUsedAmount").value(0))
        .andExpect(jsonPath("$.data.completedAt").value("2026-11-25T14:30:00"))
        .andExpect(jsonPath("$.data.canceledAt").value("2026-11-25T14:35:00"))
        .andExpect(jsonPath("$.data.walletId").doesNotExist());
  }

  @Test
  @DisplayName("한 건 조회의 가맹점 번호는 요청이 아니라 로그인한 점주의 가맹점으로 정한다")
  void 한건_내_가맹점으로_조회() throws Exception {
    // when : 남의 가맹점 번호(99)를 쿼리로 보내 본다
    mockMvc
        .perform(get("/api/merchants/me/payments/34").param("merchantId", "99"))
        .andExpect(status().isOk());

    // then : 99 가 아니라 내 가맹점 3 으로 조회했다
    verify(queryService).getOne(3L, 34L);
  }

  @Test
  @DisplayName("결제가 없거나 다른 가맹점의 결제이면 404 (PAY-003)")
  void 한건_결제없음_404() throws Exception {
    given(queryService.getOne(3L, 34L))
        .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

    mockMvc
        .perform(get("/api/merchants/me/payments/34"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("PAY-003"));
  }

  @Test
  @DisplayName("한 건 조회에서 내 가맹점이 없으면 404 (MER-001)")
  void 한건_가맹점없음_404() throws Exception {
    given(ownedResources.merchantIdOf(any()))
        .willThrow(new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

    mockMvc
        .perform(get("/api/merchants/me/payments/34"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MER-001"));
  }

  @Test
  @WithAuthUser(role = UserRole.USER)
  @DisplayName("한 건 조회를 점주가 아닌 이용자(USER)가 호출하면 403 (COM-008)")
  void 한건_이용자_403() throws Exception {
    mockMvc
        .perform(get("/api/merchants/me/payments/34"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COM-008"));

    verify(queryService, never()).getOne(anyLong(), anyLong());
  }

  @Test
  @WithAnonymousUser
  @DisplayName("한 건 조회에서 토큰이 없으면 401 (COM-005)")
  void 한건_토큰없음_401() throws Exception {
    mockMvc
        .perform(get("/api/merchants/me/payments/34"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COM-005"));
  }
}
