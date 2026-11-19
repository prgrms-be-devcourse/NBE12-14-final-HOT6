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

  // 목적: 정상 요청이 201 과 공통 응답 형식으로 내려가는지, 종료를 생략해도 되는지 확인한다.
  @Test
  @DisplayName("정상 요청이면 201 과 등록 결과를 반환한다")
  void create_created() throws Exception {
    given(policyService.create(any())).willReturn(sample());

    mockMvc
        .perform(
            post("/api/admin/cashback/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"rate": 0.03, "startsAt": "2026-11-01T00:00:00"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.policyId").value(1))
        .andExpect(jsonPath("$.data.rate").value(0.03))
        .andExpect(jsonPath("$.data.endsAt").doesNotExist());
  }

  // 목적: 필수값(요율, 시작 시각)이 빠지면 400 으로 걸러지는지 확인한다.
  @Test
  @DisplayName("요율이나 시작 시각이 없으면 400")
  void create_missingRequired() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/cashback/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"startsAt": "2026-11-01T00:00:00"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    mockMvc
        .perform(
            post("/api/admin/cashback/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rate": 0.03}
                    """))
        .andExpect(status().isBadRequest());
  }

  // 목적: 기간이 겹쳐 서비스가 예외를 던지면 409 와 CSH-002 로 변환되는지 확인한다.
  @Test
  @DisplayName("기간이 겹치면 409 와 CSH-002")
  void create_overlap() throws Exception {
    given(policyService.create(any()))
        .willThrow(new BusinessException(CashbackErrorCode.POLICY_PERIOD_OVERLAP));

    mockMvc
        .perform(
            post("/api/admin/cashback/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"rate": 0.03, "startsAt": "2026-11-01T00:00:00"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CSH-002"));
  }

  // 목적: 요율·기간 오류가 400 과 CSH-003 으로 변환되는지 확인한다.
  @Test
  @DisplayName("요율이나 기간이 올바르지 않으면 400 과 CSH-003")
  void create_invalidPolicy() throws Exception {
    given(policyService.create(any()))
        .willThrow(new BusinessException(CashbackErrorCode.INVALID_POLICY));

    mockMvc
        .perform(
            post("/api/admin/cashback/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"rate": 2, "startsAt": "2026-11-01T00:00:00"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CSH-003"));
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
