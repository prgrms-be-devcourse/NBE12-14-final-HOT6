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
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementResponse;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlementStatus;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.service.MonthlySettlementQueryService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(MonthlySettlementController.class)
@Import(SecurityConfig.class)
@WithAuthUser(role = UserRole.OWNER) // 허용 역할의 점주로 로그인한 상태로 호출한다(번호는 토큰의 회원으로 찾는다)
class MonthlySettlementControllerTest {

  private static final YearMonth FROM = YearMonth.of(2026, 9);
  private static final YearMonth TO = YearMonth.of(2026, 10);

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private MonthlySettlementQueryService queryService;
  @MockitoBean private OwnedResources ownedResources;

  // 로그인한 점주의 가맹점은 3번이다
  @BeforeEach
  void myMerchantIsThree() {
    given(ownedResources.merchantIdOf(any())).willReturn(3L);
  }

  @Test
  @DisplayName("기간을 주면 월 정산 목록을 정산월과 지급 정보와 함께 반환한다")
  void 조회성공() throws Exception {
    given(queryService.getMonthly(3L, FROM, TO))
        .willReturn(
            List.of(
                new MonthlySettlementResponse(
                    TO,
                    15_000L,
                    MonthlySettlementStatus.PAID,
                    LocalDateTime.of(2026, 11, 1, 0, 30))));

    mockMvc
        .perform(monthly("2026-09", "2026-10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].settlementMonth").value("2026-10"))
        .andExpect(jsonPath("$.data[0].payoutAmount").value(15000))
        .andExpect(jsonPath("$.data[0].status").value("PAID"));
  }

  @Test
  @DisplayName("from 이나 to 가 없으면 400 COM-001 로 응답한다")
  void 파라미터누락() throws Exception {
    mockMvc
        .perform(get("/api/merchants/me/settlements/monthly").param("from", "2026-09"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));
  }

  @Test
  @DisplayName("월 형식이 올바르지 않으면 400 COM-001 로 응답한다")
  void 월형식오류() throws Exception {
    mockMvc
        .perform(monthly("2026-09-01", "2026-10"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));
  }

  @Test
  @DisplayName("기간이 올바르지 않으면 400 STL-004 로 응답한다")
  void 기간오류() throws Exception {
    given(queryService.getMonthly(3L, FROM, TO))
        .willThrow(new BusinessException(SettlementErrorCode.INVALID_MONTH_PERIOD));

    mockMvc
        .perform(monthly("2026-09", "2026-10"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STL-004"));
  }

  @Test
  @DisplayName("가맹점이 없는 점주면 404 MER-001 로 응답한다")
  void 가맹점없음() throws Exception {
    given(ownedResources.merchantIdOf(any()))
        .willThrow(new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

    mockMvc
        .perform(monthly("2026-09", "2026-10"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MER-001"));
  }

  private MockHttpServletRequestBuilder monthly(String from, String to) {
    return get("/api/merchants/me/settlements/monthly").param("from", from).param("to", to);
  }
}
