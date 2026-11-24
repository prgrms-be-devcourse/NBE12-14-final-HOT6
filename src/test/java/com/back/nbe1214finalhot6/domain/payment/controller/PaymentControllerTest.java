package com.back.nbe1214finalhot6.domain.payment.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.idempotency.IdempotencyKeys;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.service.PinService;
import com.back.nbe1214finalhot6.domain.payment.exception.PaymentErrorCode;
import com.back.nbe1214finalhot6.domain.payment.dto.PayRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentCancelService;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentFacade;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 결제 컨트롤러 테스트. 재조회 API(`GET /api/payments/by-key/{key}`)의 요청·응답 형태를 검증한다. */
@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
@WithAuthUser(userId = 1L) // 로그인한 회원(AuthUser)으로 호출한다. 내 지갑은 setUp 에서 1번으로 정한다
class PaymentControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private PaymentFacade paymentFacade;
  @MockitoBean private PaymentQueryService paymentQueryService;
  @MockitoBean private PaymentCancelService paymentCancelService;
  @MockitoBean private OwnedResources ownedResources;
  @MockitoBean private PinService pinService;

  @BeforeEach
  void setUp() {
    // 로그인한 회원의 지갑은 1번이다
    given(ownedResources.walletIdOf(any())).willReturn(1L);
  }

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
            LocalDateTime.of(2026, 11, 20, 10, 0, 3), // 완료 시각
            null); // 취소 시각 (취소 전)
    given(paymentQueryService.findByKey(1L, key)).willReturn(response);

    // when & then : 멱등키는 경로로 보낸다. walletId 는 보내지 않고 토큰의 회원으로 서버가 찾는다 (GET 이라 본문 없음)
    mockMvc
        .perform(get("/api/payments/by-key/{key}", key))
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
            .perform(get("/api/payments/by-key/{key}", key))
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
            .perform(get("/api/payments/by-key/{key}", key))
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
                null,
                null));

    mockMvc
        .perform(get("/api/payments/by-key/{key}", key))
        .andExpect(status().isOk())
            //헤더체크
        .andExpect(header().string("Cache-Control", containsString("no-store")));
  }

  @Test
  @DisplayName("결제 요청 본문에 남의 walletId 를 넣어도 내 지갑(walletIdOf 결과)으로 결제한다")
  void 결제_본문_walletId_무시() throws Exception {
    // given : walletIdOf 는 setUp 에서 1L 을 돌려준다. 본문에는 남의 지갑 99L 을 넣는다
    String key = UUID.randomUUID().toString();
    String body =
        """
        {"walletId": 99, "qrToken": "qr-token-1", "cashbackUsed": 1000, "pin": "123456"}
        """;
    // when : 결제 요청
    mockMvc
        .perform(
            post("/api/payments")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
    // then : 본문의 99 가 아니라 내 지갑 1 로 호출됐다
    verify(paymentFacade).pay(key, new PaymentRequest(1L, "qr-token-1", 1000L));
  }

  @Test
  @DisplayName("결제 요청의 cashbackUsed 가 그대로 전달된다")
  void 결제_cashbackUsed_전달() throws Exception {
    String key = UUID.randomUUID().toString();
    String body =
            """
            {"qrToken": "qr-token-1", "cashbackUsed": 1000, "pin": "123456"}
            """;
    // when : 결제 요청
    mockMvc
            .perform(
                    post("/api/payments")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
            .andExpect(status().isOk());
    // then : cashbackUsed 1000 이 0 으로 바뀌지 않고 그대로 전달됐다
    verify(paymentFacade).pay(key, new PaymentRequest(1L, "qr-token-1", 1000L));
  }


  // 목적: 결제 전에 PIN 을 검증하는지(로그인한 회원 번호로), 그리고 PIN 검증이 결제 처리보다 먼저인지 확인한다.
  @Test
  @DisplayName("결제 전에 로그인한 회원의 PIN 을 먼저 검증한다")
  void 결제전_PIN_검증() throws Exception {
    String key = UUID.randomUUID().toString();

    mockMvc.perform(payRequest(key, "{\"qrToken\": \"qr-token-1\", \"cashbackUsed\": 0, \"pin\": \"123456\"}"))
        .andExpect(status().isOk());

    InOrder order = inOrder(pinService, paymentFacade);
    order.verify(pinService).verify(1L, "123456");
    order.verify(paymentFacade).pay(key, new PaymentRequest(1L, "qr-token-1", 0L));
  }

  // 목적: PIN 이 틀리면 결제를 시작하지 않는지(결제 기록도 QR 사용 처리도 없는지) 확인한다.
  @Test
  @DisplayName("PIN 이 틀리면 400 AUTH-005 이고 결제를 시작하지 않는다")
  void PIN_불일치면_결제_안함() throws Exception {
    willThrow(new BusinessException(AuthErrorCode.PIN_MISMATCH, "PIN이 일치하지 않습니다 (잔여 3회)"))
        .given(pinService)
        .verify(1L, "000000");

    mockMvc
        .perform(payRequest(UUID.randomUUID().toString(), "{\"qrToken\": \"qr-token-1\", \"pin\": \"000000\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH-005"))
        .andExpect(jsonPath("$.message").value("PIN이 일치하지 않습니다 (잔여 3회)"));

    verify(paymentFacade, never()).pay(any(), any());
  }

  @Test
  @DisplayName("PIN 이 잠겼으면 409 AUTH-006 이고 결제를 시작하지 않는다")
  void PIN_잠금이면_결제_안함() throws Exception {
    willThrow(new BusinessException(AuthErrorCode.PIN_LOCKED)).given(pinService).verify(anyLong(), anyString());

    mockMvc
        .perform(payRequest(UUID.randomUUID().toString(), "{\"qrToken\": \"qr-token-1\", \"pin\": \"123456\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH-006"));

    verify(paymentFacade, never()).pay(any(), any());
  }

  // 목적: PIN 이 없거나 6자리 숫자가 아니면 PIN 검증도 결제도 하기 전에 400 COM-001 로 거절하는지 확인한다(PIN 시도로 세지 않는다).
  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"qrToken\": \"qr-token-1\"}",
        "{\"qrToken\": \"qr-token-1\", \"pin\": \"\"}",
        "{\"qrToken\": \"qr-token-1\", \"pin\": \"12345\"}",
        "{\"qrToken\": \"qr-token-1\", \"pin\": \"12345a\"}"
      })
  @DisplayName("PIN 이 없거나 6자리 숫자가 아니면 400 COM-001 이고 PIN 검증도 결제도 하지 않는다")
  void PIN_형식오류(String body) throws Exception {
    mockMvc
        .perform(payRequest(UUID.randomUUID().toString(), body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));

    verify(pinService, never()).verify(anyLong(), anyString());
    verify(paymentFacade, never()).pay(any(), any());
  }

  // 목적: 응답을 못 받았을 때 쓰는 재조회 API 는 읽기만 하므로 PIN 을 요구하지 않는지 확인한다.
  @Test
  @DisplayName("재조회는 PIN 을 검증하지 않는다")
  void 재조회는_PIN_불필요() throws Exception {
    mockMvc
        .perform(get("/api/payments/by-key/{key}", UUID.randomUUID().toString()))
        .andExpect(status().isOk());

    verifyNoInteractions(pinService);
  }

  // 목적: PIN 이 로그에 남지 않도록 요청 객체의 문자열 표현에서 PIN 을 가리는지 확인한다.
  @Test
  @DisplayName("요청 객체를 문자열로 만들어도 PIN 이 보이지 않는다")
  void PIN은_로그에_안_남는다() {
    PayRequest request = new PayRequest("qr-token-1", 1000L, "123456");

    org.assertj.core.api.Assertions.assertThat(request.toString())
        .doesNotContain("123456")
        .contains("pin=****");
  }

  private MockHttpServletRequestBuilder payRequest(String key, String body) {
    return post("/api/payments")
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  @Test
  @WithAuthUser(userId = 2L, role = UserRole.OWNER)
  @DisplayName("결제 취소는 로그인한 점주의 가맹점(merchantIdOf 결과)으로 취소한다")
  void 취소_merchantIdOf_사용() throws Exception {
    // given : 내 가맹점은 5번이다
    Long paymentId = 10L;
    given(ownedResources.merchantIdOf(any())).willReturn(5L);

    // when : 결제 취소 요청 (merchantId 는 보내지 않는다)
    mockMvc.perform(post("/api/payments/{id}/cancel", paymentId))
            .andExpect(status().isOk());

    // then : 내 가맹점 5번으로 취소가 호출됐다
    verify(paymentCancelService).cancel(5L, paymentId);
  }

  @Test
  @DisplayName("USER 역할은 결제를 취소할 수 없다 (403)")
  void 취소_USER_역할_403() throws Exception {
    // given : 클래스 기본 로그인은 USER 역할이다 (취소는 OWNER 만 가능하다)

    // when : 결제 취소 요청
    mockMvc.perform(post("/api/payments/{id}/cancel", 10L))
            // then : 역할 부족으로 403 (COM-008)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-008"));

    // 취소 서비스는 호출되지 않는다
    verify(paymentCancelService, never()).cancel(any(), any());
  }
}
