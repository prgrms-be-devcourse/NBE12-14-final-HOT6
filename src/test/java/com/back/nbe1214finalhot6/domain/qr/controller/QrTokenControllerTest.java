package com.back.nbe1214finalhot6.domain.qr.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.qr.dto.QrStatusResponse;
import com.back.nbe1214finalhot6.domain.qr.entity.QrStatus;
import com.back.nbe1214finalhot6.domain.qr.exception.QrErrorCode;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
