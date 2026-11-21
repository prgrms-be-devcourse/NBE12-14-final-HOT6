package com.back.nbe1214finalhot6.domain.payment.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.idempotency.IdempotencyKeys;
import com.back.nbe1214finalhot6.domain.payment.exception.PaymentErrorCode;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentFacade;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 결제 컨트롤러 테스트. 재조회 API(`GET /api/payments/by-key/{key}`)의 요청·응답 형태를 검증한다. */
@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PaymentFacade paymentFacade;
  @MockitoBean private PaymentQueryService paymentQueryService;

  @Test
  @DisplayName("재조회 성공 시 200 과 영수증 필드(상태, 사용분, 적립액, 요율, 지급 예정 시각, 제외 사유, 실패 코드)를 반환한다")
  void 재조회성공_응답필드() throws Exception {
    // given : 서비스가 완료된 결제 결과를 돌려준다
    String key = UUID.randomUUID().toString();
    PaymentResponse response =
        new PaymentResponse(
            10L,
            PaymentStatus.COMPLETED,
            10_000L, // 결제 금액
            9_000L, // 데브머니 사용분
            1_000L, // 캐시백 사용분
            900L, // 적립 캐시백
            new BigDecimal("0.1000"), // 적용 요율
            LocalDateTime.of(2026, 11, 23, 10, 30), // 지급 예정 시각
            "NONE", // 적립 제외 사유
            null, // 실패 코드 (완료 건이라 없음)
            LocalDateTime.of(2026, 11, 20, 10, 0, 0), // 접수 시각
            LocalDateTime.of(2026, 11, 20, 10, 0, 3)); // 완료 시각
    given(paymentQueryService.findByKey(1L, key)).willReturn(response);

    // when & then : 멱등키는 경로, walletId 는 쿼리 파라미터로 보낸다 (GET 이라 본문 없음)
    mockMvc
        .perform(get("/api/payments/by-key/{key}", key).param("walletId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.paymentId").value(10))
        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.amount").value(10_000))
        .andExpect(jsonPath("$.data.walletUsedAmount").value(9_000))
        .andExpect(jsonPath("$.data.cashbackUsedAmount").value(1_000))
        .andExpect(jsonPath("$.data.cashbackEarnedAmount").value(900))
        .andExpect(jsonPath("$.data.appliedRate").value(0.1))
        .andExpect(jsonPath("$.data.cashbackPayableAt").value("2026-11-23T10:30:00"))
        .andExpect(jsonPath("$.data.cashbackExclusionReason").value("NONE"))
        .andExpect(jsonPath("$.data.failureCode").value(nullValue()))
        .andExpect(jsonPath("$.data.requestedAt").value("2026-11-20T10:00:00"))
        .andExpect(jsonPath("$.data.completedAt").value("2026-11-20T10:00:03"));
  }

  @Test
  @DisplayName("멱등키 형식이 UUID 가 아니면 400 (COM-001)")
  void 키형식오류() throws Exception {
    // given : UUID 형식이 아닌 키 (서비스는 호출되기 전에 컨트롤러에서 거절된다)
    String key = "1-1-1-1-1";

    // when & then
    mockMvc
            .perform(get("/api/payments/by-key/{key}", key).param("walletId", "1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-001"));
  }

  @Test
  @DisplayName("walletId 쿼리 파라미터가 없으면 400")
  void walletId없음() throws Exception {
    String key = UUID.randomUUID().toString();

    // when & then
    mockMvc
            .perform(get("/api/payments/by-key/{key}", key).param("", "1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-001"));
  }

  @Test
  @DisplayName("없는 키이면 404 (PAY-003)")
  void 없는키_404() throws Exception {
    // given : 서비스가 "없는 키"라고 답한다
    String key = UUID.randomUUID().toString();
    given(paymentQueryService.findByKey(1L, key))
            .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

    // when & then
    mockMvc
            .perform(get("/api/payments/by-key/{key}", key).param("walletId", "1"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PAY-003"));
  }

  @Test
  @DisplayName("재조회 응답은 캐시되지 않는다 (Cache-Control: no-store)")
  void 캐시방지헤더() throws Exception {
    String key = UUID.randomUUID().toString();
    given(paymentQueryService.findByKey(1L, key))
        .willReturn(
            new PaymentResponse(
                10L,
                PaymentStatus.PROCESSING,
                10_000L,
                0L,
                0L,
                0L,
                null,
                null,
                "NONE",
                null,
                null,
                null));

    mockMvc
        .perform(get("/api/payments/by-key/{key}", key).param("walletId", "1"))
        .andExpect(status().isOk())
            //헤더체크
        .andExpect(header().string("Cache-Control", containsString("no-store")));
  }
}
