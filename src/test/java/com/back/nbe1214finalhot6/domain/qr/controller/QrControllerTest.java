package com.back.nbe1214finalhot6.domain.qr.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateRequest;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateResponse;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import java.time.LocalDateTime;
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
class QrControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private QrService qrService;

  @Test
  @DisplayName("정상 요청이면 201 과 토큰, 금액, 만료 시각을 반환한다")
  void 동적QR생성성공() throws Exception {
    given(qrService.createDynamic(eq(1L), any()))
        .willReturn(new QrCreateResponse("token-1", 5000L, LocalDateTime.of(2026, 11, 18, 9, 3)));

    mockMvc
        .perform(createRequest(1L, "{\"amount\": 5000}"))
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
  @ValueSource(strings = {"{}", "{\"amount\": null}", "{\"amount\": 0}", "{\"amount\": -1000}"})
  @DisplayName("금액이 없거나 1원 미만이면 400")
  void 금액이잘못되면400(String body) throws Exception {
    expectInvalidRequest(createRequest(1L, body));
  }

  @Test
  @DisplayName("금액이 숫자가 아니면 400")
  void 금액이숫자가아니면400() throws Exception {
    expectInvalidRequest(createRequest(1L, "{\"amount\": \"오천원\"}"));
  }

  @Test
  @DisplayName("존재하지 않는 가맹점이면 404")
  void 없는가맹점이면404() throws Exception {
    given(qrService.createDynamic(eq(999L), any()))
        .willThrow(new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

    mockMvc
        .perform(createRequest(999L, "{\"amount\": 5000}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("MER-001"));
  }

  private RequestBuilder createRequest(Long merchantId, String body) {
    return post("/api/merchants/{merchantId}/qr/dynamic", merchantId)
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
