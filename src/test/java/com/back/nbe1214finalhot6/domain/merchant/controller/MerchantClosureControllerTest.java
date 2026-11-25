package com.back.nbe1214finalhot6.domain.merchant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.merchant.dto.ClosurePreviewResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.ClosureResponse;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantClosureService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 가맹점 해지 신청 컨트롤러 테스트 (이슈 #226). */
@WebMvcTest(MerchantClosureController.class)
@Import(SecurityConfig.class)
@WithAuthUser(role = UserRole.OWNER)
class MerchantClosureControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private MerchantClosureService closureService;
  @MockitoBean private OwnedResources ownedResources;

  // 로그인한 점주의 가맹점은 3번이다
  @BeforeEach
  void myMerchantIsThree() {
    given(ownedResources.merchantIdOf(any())).willReturn(3L);
  }

  @Test
  @DisplayName("미리보기는 200 과 정산 현황, 신청 가능 여부를 반환한다")
  void 미리보기_성공() throws Exception {
    given(closureService.preview(3L))
        .willReturn(new ClosurePreviewResponse(MerchantStatus.ACTIVE, 50_000, 30_000, 8_000, 3_000, true, 0));

    mockMvc
        .perform(get("/api/merchants/me/closure/preview"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.unpaidAmount").value(50_000))
        .andExpect(jsonPath("$.data.offsettableAmount").value(30_000))
        .andExpect(jsonPath("$.data.carryDebt").value(8_000))
        .andExpect(jsonPath("$.data.accountBalance").value(3_000))
        .andExpect(jsonPath("$.data.closable").value(true))
        .andExpect(jsonPath("$.data.shortfall").value(0));
  }

  @Test
  @DisplayName("신청은 201 과 해지 진행 상태, 접수 시각을 반환한다")
  void 신청_성공() throws Exception {
    given(closureService.request(3L))
        .willReturn(new ClosureResponse(MerchantStatus.CLOSING, LocalDateTime.of(2026, 11, 25, 10, 0)));

    mockMvc
        .perform(post("/api/merchants/me/closure"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("CLOSING"))
        .andExpect(jsonPath("$.data.requestedAt").value("2026-11-25T10:00:00"));
  }

  @Test
  @DisplayName("가맹점 번호는 요청이 아니라 로그인한 점주의 가맹점(merchantIdOf 결과)으로 신청한다")
  void 내_가맹점으로_신청() throws Exception {
    mockMvc.perform(post("/api/merchants/me/closure").param("merchantId", "99")).andExpect(status().isCreated());

    verify(closureService).request(3L);
  }

  @Test
  @DisplayName("이미 해지 신청한 가맹점이면 409 (MER-003)")
  void 중복_신청_409() throws Exception {
    given(closureService.request(3L))
        .willThrow(new BusinessException(MerchantErrorCode.MERCHANT_CLOSING));

    mockMvc
        .perform(post("/api/merchants/me/closure"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("MER-003"));
  }

  @Test
  @DisplayName("부족분을 갚을 수 없으면 409 (MER-005)")
  void 부족분_409() throws Exception {
    given(closureService.request(3L))
        .willThrow(new BusinessException(MerchantErrorCode.MERCHANT_CLOSURE_SHORTFALL));

    mockMvc
        .perform(post("/api/merchants/me/closure"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MER-005"));
  }

  @Test
  @DisplayName("내 가맹점이 없으면 404 (MER-001)")
  void 가맹점없음_404() throws Exception {
    given(ownedResources.merchantIdOf(any()))
        .willThrow(new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

    mockMvc
        .perform(get("/api/merchants/me/closure/preview"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MER-001"));
  }

  @Test
  @WithAuthUser(role = UserRole.USER)
  @DisplayName("점주가 아닌 이용자(USER)가 신청하면 403 이고 서비스는 호출되지 않는다")
  void 이용자_403() throws Exception {
    mockMvc.perform(post("/api/merchants/me/closure")).andExpect(status().isForbidden());

    verify(closureService, never()).request(any());
  }

  @Test
  @WithAnonymousUser
  @DisplayName("토큰이 없으면 401 (COM-005)")
  void 토큰없음_401() throws Exception {
    mockMvc
        .perform(post("/api/merchants/me/closure"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COM-005"));
  }
}
