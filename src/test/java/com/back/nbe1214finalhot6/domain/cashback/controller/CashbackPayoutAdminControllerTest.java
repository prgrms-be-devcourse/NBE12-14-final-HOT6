package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunDetailResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunSummaryResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.PayoutItemResult;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutStatusResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.PayoutTrigger;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackPayoutService;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackPayoutStatusService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 로그인한 사용자(허용 역할)로 호출해서 보안 설정을 거쳐도 통과하는지 함께 확인한다.
@WebMvcTest(CashbackPayoutAdminController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN") // 보안 설정을 거쳐도 통과하도록 해당 경로의 허용 역할로 로그인한 상태로 호출한다
class CashbackPayoutAdminControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private CashbackPayoutService payoutService;
  @MockitoBean private CashbackPayoutStatusService statusService;

  // 목적: 현황 응답에 대기·지급 가능 집계가 담기는지 확인한다.
  @Test
  @DisplayName("지급 배치 현황은 대기 집계를 반환한다")
  void status_ok() throws Exception {
    given(statusService.status())
        .willReturn(
            new PayoutStatusResponse(
                5,
                1_500,
                2,
                600,
                LocalDateTime.of(2026, 11, 22, 0, 0)));

    mockMvc
        .perform(get("/api/admin/cashback/payouts/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pendingCount").value(5))
        .andExpect(jsonPath("$.data.dueAmount").value(600))
        .andExpect(jsonPath("$.data.nextPayableAt").value("2026-11-22T00:00:00"));
  }

  private static PayoutRunSummaryResponse summary() {
    return new PayoutRunSummaryResponse(
        7L, LocalDateTime.of(2026, 11, 21, 10, 0), PayoutTrigger.MANUAL, 3, 1, 2);
  }

  // 목적: 실행 목록이 페이지 형식으로 오고, 페이지 값이 서비스에 전달되는지 확인한다.
  @Test
  @DisplayName("실행 목록은 페이지로 반환한다")
  void runs_ok() throws Exception {
    given(statusService.runs(1, 20))
        .willReturn(new PageResponse<>(List.of(summary()), 1, 20, 21, 2));

    mockMvc
        .perform(get("/api/admin/cashback/payouts/runs").param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].runId").value(7))
        .andExpect(jsonPath("$.data.content[0].trigger").value("MANUAL"))
        .andExpect(jsonPath("$.data.totalPages").value(2));
  }

  // 목적: 잘못된 페이지 값은 400 으로 거절하는지 확인한다.
  @Test
  @DisplayName("음수 페이지는 400 이다")
  void runs_rejectsNegativePage() throws Exception {
    mockMvc
        .perform(get("/api/admin/cashback/payouts/runs").param("page", "-1"))
        .andExpect(status().isBadRequest());
  }

  // 목적: 상세에 지급 금액 합계와 실패 사유가 담기는지 확인한다.
  @Test
  @DisplayName("실행 상세는 항목과 실패 사유를 반환한다")
  void runDetail_ok() throws Exception {
    given(statusService.detail(7L))
        .willReturn(
            new PayoutRunDetailResponse(
                summary(),
                900,
                List.of(
                    new PayoutRunDetailResponse.Item(
                        1L, 2L, 3L, 300, PayoutItemResult.FAILED, "WAL-002", "탈퇴한 지갑"))));

    mockMvc
        .perform(get("/api/admin/cashback/payouts/runs/7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.paidAmount").value(900))
        .andExpect(jsonPath("$.data.run.runId").value(7))
        .andExpect(jsonPath("$.data.items[0].result").value("FAILED"))
        .andExpect(jsonPath("$.data.items[0].errorCode").value("WAL-002"));
  }

  // 목적: 없는 실행은 404(CSH-013)인지 확인한다.
  @Test
  @DisplayName("없는 실행 상세는 404 이다")
  void runDetail_notFound() throws Exception {
    given(statusService.detail(99L))
        .willThrow(new BusinessException(CashbackErrorCode.PAYOUT_RUN_NOT_FOUND));

    mockMvc
        .perform(get("/api/admin/cashback/payouts/runs/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CSH-013"));
  }

  // 목적: 운영자가 아니면 현황을 볼 수 없는지 확인한다.
  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("이용자는 지급 배치 현황과 실행 기록을 조회할 수 없다")
  void status_forbiddenForUser() throws Exception {
    mockMvc.perform(get("/api/admin/cashback/payouts/status")).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/cashback/payouts/runs")).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/cashback/payouts/runs/1")).andExpect(status().isForbidden());
  }

  // 목적: 수동 실행이 200 과 공통 응답 형식으로 처리·넘김·실패 건수를 내려 주는지 확인한다.
  @Test
  @DisplayName("지급 배치 수동 실행은 200 과 건수를 반환한다")
  void run_ok() throws Exception {
    given(payoutService.runAndRecord(PayoutTrigger.MANUAL))
        .willReturn(new PayoutRunResponse(3, 1, 2));

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
