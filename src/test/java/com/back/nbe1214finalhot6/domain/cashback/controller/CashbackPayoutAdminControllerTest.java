package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackPayoutService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 인증 없이(로그인·CSRF 토큰 없이) 호출해서 임시 보안 설정도 함께 확인한다.
@WebMvcTest(CashbackPayoutAdminController.class)
@Import(SecurityConfig.class)
class CashbackPayoutAdminControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private CashbackPayoutService payoutService;

  // 목적: 수동 실행이 200 과 공통 응답 형식으로 처리·넘김·실패 건수를 내려 주는지 확인한다.
  @Test
  @DisplayName("지급 배치 수동 실행은 200 과 건수를 반환한다")
  void run_ok() throws Exception {
    given(payoutService.run()).willReturn(new PayoutRunResponse(3, 1, 2));

    mockMvc
        .perform(post("/api/admin/cashback/payouts/run"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.paid").value(3))
        .andExpect(jsonPath("$.data.skipped").value(1))
        .andExpect(jsonPath("$.data.failed").value(2));
  }

  // 목적: 실행 경로는 POST 만 받는지(GET 은 거절) 확인한다.
  @Test
  @DisplayName("GET 으로는 실행할 수 없다")
  void run_rejectsGet() throws Exception {
    mockMvc.perform(get("/api/admin/cashback/payouts/run")).andExpect(status().is4xxClientError());
  }
}
