package com.back.nbe1214finalhot6.domain.qr.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.qr.dto.QrScanResponse;
import com.back.nbe1214finalhot6.domain.qr.dto.QrStatusResponse;
import com.back.nbe1214finalhot6.domain.qr.entity.QrStatus;
import com.back.nbe1214finalhot6.domain.qr.exception.QrErrorCode;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QrTokenController.class)
@Import(SecurityConfig.class)
class QrTokenControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private QrService qrService;

  @Test
  @DisplayName("스캔 조회 시 QR 식별자, 가맹점, 금액, 적립 대상 여부, 만료 시각을 반환한다")
  void 스캔조회성공() throws Exception {
    given(qrService.scan("token-1"))
        .willReturn(
            new QrScanResponse(
                3L, "테스트 가맹점", 5000L, true, LocalDateTime.of(2026, 11, 18, 9, 3)));

    mockMvc
        .perform(get("/api/qr/{token}", "token-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.merchantId").value(3))
        .andExpect(jsonPath("$.data.merchantName").value("테스트 가맹점"))
        .andExpect(jsonPath("$.data.amount").value(5000))
        .andExpect(jsonPath("$.data.cashbackEligible").value(true))
        .andExpect(jsonPath("$.data.expiresAt").value("2026-11-18T09:03:00"));
  }

  @ParameterizedTest
  @CsvSource({"QR_NOT_FOUND,404,QR-001", "QR_EXPIRED,410,QR-002", "QR_ALREADY_USED,409,QR-003"})
  @DisplayName("스캔 조회 시 없거나 만료되었거나 이미 사용된 QR 이면 각각의 상태 코드로 응답한다")
  void 스캔조회실패(QrErrorCode errorCode, int httpStatus, String code) throws Exception {
    given(qrService.scan("token-x")).willThrow(new BusinessException(errorCode));

    mockMvc
        .perform(get("/api/qr/{token}", "token-x"))
        .andExpect(status().is(httpStatus))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(code));
  }

  @Test
  @DisplayName("존재하는 토큰이면 200 과 상태, 만료 시각을 반환한다")
  void 상태조회성공() throws Exception {
    given(qrService.getStatus("token-1"))
        .willReturn(
            new QrStatusResponse(QrStatus.WAITING, LocalDateTime.of(2026, 11, 18, 9, 3)));

    mockMvc
        .perform(get("/api/qr/{token}/status", "token-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("WAITING"))
        .andExpect(jsonPath("$.data.expiresAt").value("2026-11-18T09:03:00"));
  }

  @Test
  @DisplayName("존재하지 않는 토큰이면 404")
  void 없는토큰이면404() throws Exception {
    given(qrService.getStatus("unknown"))
        .willThrow(new BusinessException(QrErrorCode.QR_NOT_FOUND));

    mockMvc
        .perform(get("/api/qr/{token}/status", "unknown"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("QR-001"));
  }
}
