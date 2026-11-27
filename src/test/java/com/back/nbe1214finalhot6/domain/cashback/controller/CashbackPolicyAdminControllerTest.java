package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.cashback.entity.PolicyBasis;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReservationResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyHistoryResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.PolicyHistoryAction;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackPolicyService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

// 로그인한 사용자(허용 역할)로 호출해서 보안 설정을 거쳐도 통과하는지 함께 확인한다.
@WebMvcTest(CashbackPolicyAdminController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN") // 보안 설정을 거쳐도 통과하도록 해당 경로의 허용 역할로 로그인한 상태로 호출한다
class CashbackPolicyAdminControllerTest {

  private static final LocalDateTime NOV_1 = LocalDateTime.of(2026, 11, 1, 0, 0);

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private CashbackPolicyService policyService;

  private PolicyResponse sample() {
    return new PolicyResponse(1L, new BigDecimal("0.0300"), NOV_1, null, NOV_1.minusDays(1));
  }

  private static final String BODY =
      """
      {"rate": 0.03, "effectiveAt": "2026-11-20T00:00:00", "reason": "수요 급증",
       "notice": {"title": "적립률이 바뀌어요", "content": "본문", "showOnHome": true, "homeUntil": "2026-11-30"}}
      """;

  // 목적: 인하 요청이 201 과 공통 응답 형식으로 내려가고 만들어진 구간이 담기는지 확인한다.
  @Test
  @DisplayName("인하 요청이면 201 과 만들어진 구간을 반환한다")
  void reduce_created() throws Exception {
    given(policyService.reduceRate(any(), eq(99L))).willReturn(sample());

    mockMvc
        .perform(
            post("/api/admin/cashback/policies/reductions")
                .with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.policyId").value(1))
        .andExpect(jsonPath("$.data.rate").value(0.03));
  }

  // 목적: 필수값(요율, 적용 시각)이 빠지면 400 으로 걸러지는지 확인한다.
  @Test
  @DisplayName("요율이나 적용 시각이 없으면 400")
  void reduce_missingRequired() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/cashback/policies/reductions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"effectiveAt": "2026-11-20T00:00:00"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    mockMvc
        .perform(
            post("/api/admin/cashback/policies/reductions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rate": 0.03}
                    """))
        .andExpect(status().isBadRequest());
  }

  // 목적: 사유가 없거나 공백이거나 200자를 넘으면 400 으로 거르고 서비스를 부르지 않는지 확인한다.
  @Test
  @DisplayName("사유가 없거나 200자를 넘으면 400")
  void reduce_invalidReason() throws Exception {
    for (String reason : new String[] {"", ", \"reason\": \"  \"", ", \"reason\": \"" + "가".repeat(201) + "\""}) {
      mockMvc
          .perform(
              post("/api/admin/cashback/policies/reductions")
                  .with(operator())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rate\": 0.03, \"effectiveAt\": \"2026-11-20T00:00:00\"" + reason + "}"))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(policyService);
  }

  private static RequestPostProcessor operator() {
    return authentication(
        new UsernamePasswordAuthenticationToken(
            new AuthUser(99L, UserRole.ADMIN),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  // 목적: 공지가 없거나 제목이 비면 400 인지 확인한다.
  @Test
  @DisplayName("공지가 없으면 400")
  void reduce_withoutNotice() throws Exception {
    for (String notice : new String[] {"", ", \"notice\": {\"title\": \"\", \"content\": \"본문\"}"}) {
      mockMvc
          .perform(
              post("/api/admin/cashback/policies/reductions")
                  .with(operator())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"rate\": 0.03, \"effectiveAt\": \"2026-11-20T00:00:00\", \"reason\": \"사유\"" + notice + "}"))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(policyService);
  }

  // 목적: 예약 수정이 운영자를 넘겨 200 을 반환하고, 조회는 지금 값을 담는지 확인한다.
  @Test
  @DisplayName("예약한 인하를 조회하고 수정한다")
  void reservation_getAndRevise() throws Exception {
    given(policyService.getReservation(1L))
        .willReturn(
            new RateReservationResponse(
                1L, new BigDecimal("0.03"), LocalDateTime.of(2026, 11, 20, 0, 0), "사유", null));
    given(policyService.reviseReduction(eq(1L), any(), eq(99L))).willReturn(sample());

    mockMvc
        .perform(get("/api/admin/cashback/policies/1/reduction"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reason").value("사유"));
    mockMvc
        .perform(
            put("/api/admin/cashback/policies/1/reduction")
                .with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.policyId").value(1));
  }

  // 목적: 서비스가 던지는 도메인 예외가 각각 올바른 상태 코드와 에러 코드로 변환되는지 확인한다.
  @ParameterizedTest
  @CsvSource({
    "INVALID_POLICY, 400, CSH-003",
    "BUDGET_NOT_FOUND, 404, CSH-004",
    "POLICY_PERIOD_OVERLAP, 409, CSH-002",
    "RATE_REDUCTION_LIMIT_EXCEEDED, 409, CSH-009",
    "POLICY_ALREADY_APPLIED, 409, CSH-017"
  })
  @DisplayName("인하 실패는 에러 코드에 맞는 상태로 변환된다")
  void reduce_errors(CashbackErrorCode errorCode, int httpStatus, String code) throws Exception {
    given(policyService.reduceRate(any(), any())).willThrow(new BusinessException(errorCode));

    mockMvc
        .perform(
            post("/api/admin/cashback/policies/reductions")
                .with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().is(httpStatus))
        .andExpect(jsonPath("$.code").value(code));
  }

  // 목적: 구간을 직접 등록하던 POST 는 없어졌고(조회만 남음) 405 로 거절되는지 확인한다.
  @Test
  @DisplayName("구간 직접 등록(POST /policies)은 지원하지 않는다")
  void create_removed() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/cashback/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rate\": 0.03, \"startsAt\": \"2026-11-01T00:00:00\"}"))
        .andExpect(status().isMethodNotAllowed());
  }

  // 목적: 목록 조회가 200 과 배열로 내려가는지 확인한다.
  @Test
  @DisplayName("목록 조회는 200 과 정책 배열을 반환한다")
  void list_ok() throws Exception {
    given(policyService.findAll()).willReturn(List.of(sample()));

    mockMvc
        .perform(get("/api/admin/cashback/policies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].policyId").value(1));
  }

  // 목적: 단건 조회가 200 으로 내려가고, 없는 ID 는 404 와 CSH-001 로 변환되는지 확인한다.
  @Test
  @DisplayName("단건 조회는 200, 없는 정책은 404 와 CSH-001")
  void get_okAndNotFound() throws Exception {
    given(policyService.get(1L)).willReturn(sample());
    given(policyService.get(2L))
        .willThrow(new BusinessException(CashbackErrorCode.POLICY_NOT_FOUND));

    mockMvc
        .perform(get("/api/admin/cashback/policies/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.policyId").value(1));

    mockMvc
        .perform(get("/api/admin/cashback/policies/2"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CSH-001"));
  }

  // 목적: 이력 조회가 단건 조회 경로(/{policyId})와 겹치지 않고 /history 로 가서 목록과 변경 전·후 값을 내려 주는지 확인한다.
  @Test
  @DisplayName("이력 조회는 /history 로 가서 변경 전후 값을 반환한다")
  void history_ok() throws Exception {
    PolicyHistoryResponse created =
        new PolicyHistoryResponse(
            2L,
            7L,
            PolicyHistoryAction.CREATE,
            null,
            LocalDateTime.of(2026, 11, 18, 9, 0),
            null,
            null,
            null,
            new BigDecimal("0.0200"),
            LocalDateTime.of(2026, 11, 18, 12, 0),
            LocalDateTime.of(2026, 12, 1, 0, 0),
            "수요 급증",
            PolicyBasis.rateOnly(new BigDecimal("0.03")));
    given(policyService.findHistory(7L, 1, 5))
        .willReturn(new PageResponse<>(List.of(created), 1, 5, 6L, 2));

    mockMvc
        .perform(
            get("/api/admin/cashback/policies/history")
                .param("policyId", "7")
                .param("page", "1")
                .param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalElements").value(6))
        .andExpect(jsonPath("$.data.content[0].action").value("CREATE"))
        .andExpect(jsonPath("$.data.content[0].policyId").value(7))
        .andExpect(jsonPath("$.data.content[0].beforeRate").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].afterRate").value(0.02))
        .andExpect(jsonPath("$.data.content[0].reason").value("수요 급증"))
        .andExpect(jsonPath("$.data.content[0].basis.previousRate").value(0.03));
  }

  // 목적: 필터와 페이지를 생략하면 전체, 첫 페이지, 기본 크기 20 으로 조회하는지 확인한다.
  @Test
  @DisplayName("이력 조회는 파라미터를 생략하면 전체 첫 페이지를 기본 크기로 조회한다")
  void history_defaults() throws Exception {
    given(policyService.findHistory(null, 0, 20))
        .willReturn(new PageResponse<>(List.of(), 0, 20, 0L, 0));

    mockMvc
        .perform(get("/api/admin/cashback/policies/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty());

    verify(policyService).findHistory(null, 0, 20);
  }

  // 목적: 페이지 번호·크기가 범위를 벗어나거나 정책 ID 가 숫자가 아니면 400 으로 걸러지는지 확인한다.
  @Test
  @DisplayName("이력 조회의 잘못된 파라미터는 400")
  void history_invalidParameters() throws Exception {
    mockMvc
        .perform(get("/api/admin/cashback/policies/history").param("page", "-1"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/admin/cashback/policies/history").param("size", "0"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/admin/cashback/policies/history").param("policyId", "abc"))
        .andExpect(status().isBadRequest());
  }
}
