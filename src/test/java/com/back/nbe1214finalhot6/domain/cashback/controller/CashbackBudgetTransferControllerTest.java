package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetTransferResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetTransferType;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetTransferService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CashbackBudgetTransferController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN") // 보안 설정을 거쳐도 통과하도록 해당 경로의 허용 역할로 로그인한 상태로 호출한다
class CashbackBudgetTransferControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private CashbackBudgetTransferService transferService;

  private BudgetTransferResponse sample() {
    return new BudgetTransferResponse(
        1L, 2026, 11, 2026, 12, 300_000L, BudgetTransferType.MANUAL, null,
        LocalDateTime.of(2026, 11, 18, 9, 0));
  }

  // 목적: 이월 POST 가 201 과 공통 응답 형식으로 넘긴 달·받은 달·금액·종류를 내려 주는지 확인한다.
  @Test
  @DisplayName("이월 POST 는 201 과 이력을 반환한다")
  void transfer_created() throws Exception {
    given(transferService.transfer(any())).willReturn(sample());

    mockMvc
        .perform(
            post("/api/admin/cashback/budget/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"year": 2026, "month": 11, "amount": 300000}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.fromMonth").value(11))
        .andExpect(jsonPath("$.data.toMonth").value(12))
        .andExpect(jsonPath("$.data.amount").value(300_000))
        .andExpect(jsonPath("$.data.type").value("MANUAL"));
  }

  // 목적: 연·월·금액 중 하나라도 빠지면 400 으로 걸러지는지 확인한다.
  @Test
  @DisplayName("연·월·금액이 빠지면 400")
  void transfer_missingFields() throws Exception {
    for (String body :
        List.of("{}", "{\"year\":2026,\"month\":11}", "{\"year\":2026,\"amount\":1}", "{\"month\":11,\"amount\":1}")) {
      mockMvc
          .perform(
              post("/api/admin/cashback/budget/transfers")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
  }

  // 목적: 남은 예산보다 많이 넘기려는 요청이 409 와 CSH-011, 월 예산이 없으면 404 와 CSH-004 로 내려가는지 확인한다.
  @Test
  @DisplayName("남은 예산보다 많으면 409 와 CSH-011, 월 예산이 없으면 404 와 CSH-004")
  void transfer_businessErrors() throws Exception {
    given(transferService.transfer(any()))
        .willThrow(new BusinessException(CashbackErrorCode.TRANSFER_EXCEEDS_REMAINING))
        .willThrow(new BusinessException(CashbackErrorCode.BUDGET_NOT_FOUND));
    String body = "{\"year\":2026,\"month\":11,\"amount\":999999999}";

    mockMvc
        .perform(post("/api/admin/cashback/budget/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CSH-011"));
    mockMvc
        .perform(post("/api/admin/cashback/budget/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CSH-004"));
  }

  // 목적: 이력 조회가 배열로 내려가고 year 파라미터가 없으면 400 인지 확인한다.
  @Test
  @DisplayName("이력 조회는 배열로 반환하고 year 가 없으면 400")
  void list() throws Exception {
    given(transferService.list(2026)).willReturn(List.of(sample()));

    mockMvc
        .perform(get("/api/admin/cashback/budget/transfers").param("year", "2026"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].fromYear").value(2026));
    mockMvc.perform(get("/api/admin/cashback/budget/transfers")).andExpect(status().isBadRequest());
  }
}
