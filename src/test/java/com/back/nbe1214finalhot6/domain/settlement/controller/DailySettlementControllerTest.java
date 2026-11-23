package com.back.nbe1214finalhot6.domain.settlement.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.dto.DailySettlementResponse;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.service.DailySettlementQueryService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DailySettlementController.class)
@Import(SecurityConfig.class)
@WithAuthUser(role = UserRole.OWNER) // 허용 역할의 점주로 로그인한 상태로 호출한다(번호는 토큰의 회원으로 찾는다)
class DailySettlementControllerTest {

  private static final LocalDate FROM = LocalDate.of(2026, 11, 15);
  private static final LocalDate TO = LocalDate.of(2026, 11, 17);

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private DailySettlementQueryService queryService;
  @MockitoBean private OwnedResources ownedResources;

  // 로그인한 점주의 가맹점은 3번이다
  @BeforeEach
  void myMerchantIsThree() {
    given(ownedResources.merchantIdOf(any())).willReturn(3L);
  }

  @Test
  @DisplayName("기간을 주면 일 정산 목록을 날짜와 금액 항목과 함께 반환한다")
  void 조회성공() throws Exception {
    given(queryService.getDaily(3L, FROM, TO))
        .willReturn(
            List.of(
                new DailySettlementResponse(
                    LocalDate.of(2026, 11, 17), 50_000L, 0L, 50_000L, -30_000L, 20_000L, 0L)));

    mockMvc
        .perform(
            get("/api/merchants/me/settlements/daily")
                .param("from", "2026-11-15")
                .param("to", "2026-11-17"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].settlementDate").value("2026-11-17"))
        .andExpect(jsonPath("$.data[0].paymentTotal").value(50000))
        .andExpect(jsonPath("$.data[0].carryIn").value(-30000))
        .andExpect(jsonPath("$.data[0].confirmedAmount").value(20000))
        .andExpect(jsonPath("$.data[0].carryOut").value(0));
  }

  @Test
  @DisplayName("from 이나 to 가 없으면 400 COM-001 로 응답한다")
  void 파라미터누락() throws Exception {
    mockMvc
        .perform(get("/api/merchants/me/settlements/daily").param("from", "2026-11-15"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));
  }

  @Test
  @DisplayName("날짜 형식이 올바르지 않으면 400 COM-001 로 응답한다")
  void 날짜형식오류() throws Exception {
    mockMvc
        .perform(
            get("/api/merchants/me/settlements/daily")
                .param("from", "2026/11/15")
                .param("to", "2026-11-17"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));
  }

  @Test
  @DisplayName("기간이 올바르지 않으면 400 STL-002 로 응답한다")
  void 기간오류() throws Exception {
    given(queryService.getDaily(3L, FROM, TO))
        .willThrow(new BusinessException(SettlementErrorCode.INVALID_PERIOD));

    mockMvc
        .perform(
            get("/api/merchants/me/settlements/daily")
                .param("from", "2026-11-15")
                .param("to", "2026-11-17"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STL-002"));
  }

  @Test
  @DisplayName("없는 가맹점이면 404 MER-001 로 응답한다")
  void 가맹점없음() throws Exception {
    given(queryService.getDaily(3L, FROM, TO))
        .willThrow(new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

    mockMvc
        .perform(
            get("/api/merchants/me/settlements/daily")
                .param("from", "2026-11-15")
                .param("to", "2026-11-17"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MER-001"));
  }
}
