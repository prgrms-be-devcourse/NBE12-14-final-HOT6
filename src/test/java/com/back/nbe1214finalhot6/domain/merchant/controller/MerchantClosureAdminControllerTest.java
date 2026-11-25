package com.back.nbe1214finalhot6.domain.merchant.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.merchant.dto.ClosureSettlementRunResult;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantClosureSettlementService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 가맹점 해지 정산 운영자 컨트롤러 테스트 (이슈 #239). */
@WebMvcTest(MerchantClosureAdminController.class)
@Import(SecurityConfig.class)
@WithAuthUser(role = UserRole.ADMIN)
class MerchantClosureAdminControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private MerchantClosureSettlementService settlementService;

  @Test
  @DisplayName("운영자가 실행하면 200 과 처리 결과를 반환한다")
  void 실행_성공() throws Exception {
    given(settlementService.run())
        .willReturn(new ClosureSettlementRunResult(2, 1, 3, 4, 0, List.of(7L)));

    mockMvc
        .perform(post("/api/admin/merchants/closures/run"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.closed").value(2))
        .andExpect(jsonPath("$.data.held").value(1))
        .andExpect(jsonPath("$.data.waiting").value(3))
        .andExpect(jsonPath("$.data.dailyIncomplete").value(4))
        .andExpect(jsonPath("$.data.failed").value(0))
        .andExpect(jsonPath("$.data.heldMerchantIds[0]").value(7));
  }

  @Test
  @WithAuthUser(role = UserRole.OWNER)
  @DisplayName("점주가 호출하면 403 이고 정산은 실행되지 않는다")
  void 점주_403() throws Exception {
    mockMvc.perform(post("/api/admin/merchants/closures/run")).andExpect(status().isForbidden());

    verify(settlementService, never()).run();
  }

  @Test
  @WithAuthUser(role = UserRole.USER)
  @DisplayName("이용자가 호출하면 403 이다")
  void 이용자_403() throws Exception {
    mockMvc.perform(post("/api/admin/merchants/closures/run")).andExpect(status().isForbidden());
  }

  @Test
  @WithAnonymousUser
  @DisplayName("토큰이 없으면 401 (COM-005)")
  void 토큰없음_401() throws Exception {
    mockMvc
        .perform(post("/api/admin/merchants/closures/run"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COM-005"));
  }
}
