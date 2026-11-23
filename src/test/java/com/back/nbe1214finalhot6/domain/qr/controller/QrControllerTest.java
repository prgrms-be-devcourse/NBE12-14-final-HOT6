package com.back.nbe1214finalhot6.domain.qr.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateRequest;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateResponse;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

@WebMvcTest(QrController.class)
@Import(SecurityConfig.class)
@WithAuthUser(role = UserRole.OWNER) // 허용 역할의 점주로 로그인한 상태로 호출한다(번호는 토큰의 회원으로 찾는다)
class QrControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private QrService qrService;
  @MockitoBean private OwnedResources ownedResources;

  // 로그인한 점주의 가맹점은 1번이다
  @BeforeEach
  void myMerchantIsOne() {
    given(ownedResources.merchantIdOf(any())).willReturn(1L);
  }

  @Test
  @DisplayName("정상 요청이면 201 과 토큰, 금액, 만료 시각을 반환한다")
  void 동적QR생성성공() throws Exception {
    given(qrService.createDynamic(eq(1L), any()))
        .willReturn(new QrCreateResponse("token-1", 5000L, LocalDateTime.of(2026, 11, 18, 9, 3)));

    mockMvc
        .perform(createRequest("{\"amount\": 5000}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.token").value("token-1"))
        .andExpect(jsonPath("$.data.amount").value(5000))
        .andExpect(jsonPath("$.data.expiresAt").value("2026-11-18T09:03:00"));

    ArgumentCaptor<QrCreateRequest> captor = ArgumentCaptor.forClass(QrCreateRequest.class);
    verify(qrService).createDynamic(eq(1L), captor.capture());
    assertThat(captor.getValue().amount()).isEqualTo(5000L);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"amount\": null}",
        "{\"amount\": 0}",
        "{\"amount\": -1000}",
        "{\"amount\": 1000001}"
      })
  @DisplayName("금액이 없거나 1원 미만이거나 결제 한도(100만원)를 넘으면 400")
  void 금액이잘못되면400(String body) throws Exception {
    expectInvalidRequest(createRequest(body));
  }

  @ParameterizedTest
  @ValueSource(longs = {1L, 1_000_000L})
  @DisplayName("금액이 1원과 결제 한도(100만원) 경계값이면 201")
  void 금액경계값이면성공(long amount) throws Exception {
    given(qrService.createDynamic(eq(1L), any()))
        .willReturn(new QrCreateResponse("token-1", amount, LocalDateTime.of(2026, 11, 18, 9, 3)));

    mockMvc
        .perform(createRequest("{\"amount\": " + amount + "}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.amount").value(amount));
  }

  @Test
  @DisplayName("금액이 숫자가 아니면 400")
  void 금액이숫자가아니면400() throws Exception {
    expectInvalidRequest(createRequest("{\"amount\": \"오천원\"}"));
  }

  // 목적: 로그인한 점주의 가맹점이 없으면(공용 도구가 MER-001) 404 로 응답하고 QR 을 만들지 않는지 확인한다.
  @Test
  @DisplayName("내 가맹점이 없으면 404")
  void 내가맹점이없으면404() throws Exception {
    given(ownedResources.merchantIdOf(any()))
        .willThrow(new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

    mockMvc
        .perform(createRequest("{\"amount\": 5000}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("MER-001"));
    verify(qrService, never()).createDynamic(any(), any());
  }

  private RequestBuilder createRequest(String body) {
    return post("/api/merchants/me/qr/dynamic")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  // 요청이 400(COM-001)으로 거절되고, 서비스가 새로 호출되지 않았는지 확인한다.
  private void expectInvalidRequest(RequestBuilder request) throws Exception {
    int callsBefore = mockingDetails(qrService).getInvocations().size();
    mockMvc
        .perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("COM-001"));
    assertThat(mockingDetails(qrService).getInvocations()).hasSize(callsBefore);
  }
}
