package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 인증 없이(로그인·CSRF 토큰 없이) 호출해서 임시 보안 설정도 함께 확인한다.
@WebMvcTest(CashbackPolicyAdminController.class)
@Import(SecurityConfig.class)
class CashbackPolicyAdminControllerTest {

  private static final LocalDateTime NOV_1 = LocalDateTime.of(2026, 11, 1, 0, 0);

  @Autowired private MockMvc mockMvc;
  @MockitoBean private CashbackPolicyService policyService;

  private PolicyResponse sample() {
    return new PolicyResponse(1L, new BigDecimal("0.0300"), NOV_1, null, NOV_1.minusDays(1));
  }

  private static final String BODY =
      """
      {"rate": 0.03, "effectiveAt": "2026-11-20T00:00:00"}
      """;

  // 목적: 인하 요청이 201 과 공통 응답 형식으로 내려가고 만들어진 구간이 담기는지 확인한다.
  @Test
  @DisplayName("인하 요청이면 201 과 만들어진 구간을 반환한다")
  void reduce_created() throws Exception {
    given(policyService.reduceRate(any())).willReturn(sample());

    mockMvc
        .perform(
            post("/api/admin/cashback/policies/reductions")
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

  // 목적: 서비스가 던지는 도메인 예외가 각각 올바른 상태 코드와 에러 코드로 변환되는지 확인한다.
  @ParameterizedTest
  @CsvSource({
    "INVALID_POLICY, 400, CSH-003",
    "BUDGET_NOT_FOUND, 404, CSH-004",
    "POLICY_PERIOD_OVERLAP, 409, CSH-002",
    "RATE_REDUCTION_LIMIT_EXCEEDED, 409, CSH-009"
  })
  @DisplayName("인하 실패는 에러 코드에 맞는 상태로 변환된다")
  void reduce_errors(CashbackErrorCode errorCode, int httpStatus, String code) throws Exception {
    given(policyService.reduceRate(any())).willThrow(new BusinessException(errorCode));

    mockMvc
        .perform(
            post("/api/admin/cashback/policies/reductions")
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
}
