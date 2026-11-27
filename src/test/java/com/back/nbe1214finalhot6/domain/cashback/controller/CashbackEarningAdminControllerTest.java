package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.cashback.dto.AdminEarningResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackEarningAdminService;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(CashbackEarningAdminController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class CashbackEarningAdminControllerTest {

  private static final String PATH = "/api/admin/cashback/earnings";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private CashbackEarningAdminService earningService;

  // 목적: 조건이 서비스에 그대로 전달되고 지갑 번호가 담긴 페이지가 오는지 확인한다.
  @Test
  @DisplayName("조건을 넘겨 페이지로 반환한다")
  void search_ok() throws Exception {
    given(
            earningService.search(
                EarningStatus.PENDING,
                7L,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                1,
                20))
        .willReturn(
            new PageResponse<>(
                List.of(
                    new AdminEarningResponse(
                        1L,
                        2L,
                        7L,
                        EarningStatus.PENDING,
                        10_000L,
                        300L,
                        new BigDecimal("0.03"),
                        LocalDateTime.of(2026, 11, 10, 9, 0),
                        LocalDateTime.of(2026, 11, 14, 0, 0),
                        null,
                        null,
                        CashbackExclusionReason.NONE)),
                1,
                20,
                21,
                2));

    mockMvc
        .perform(
            get(PATH)
                .param("status", "PENDING")
                .param("walletId", "7")
                .param("from", "2026-11-01")
                .param("to", "2026-11-30")
                .param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].walletId").value(7))
        .andExpect(jsonPath("$.data.totalElements").value(21));
  }

  // 목적: 없는 상태, 날짜 형식 오류, 0 이하 지갑 번호, 음수 페이지는 400 인지 확인한다.
  @Test
  @DisplayName("잘못된 조건은 400 이다")
  void search_invalid() throws Exception {
    mockMvc.perform(get(PATH).param("status", "NOPE")).andExpect(status().isBadRequest());
    mockMvc.perform(get(PATH).param("from", "2026/11/01")).andExpect(status().isBadRequest());
    mockMvc.perform(get(PATH).param("walletId", "0")).andExpect(status().isBadRequest());
    mockMvc.perform(get(PATH).param("page", "-1")).andExpect(status().isBadRequest());
  }

  // 목적: 이용자는 전체 적립 내역을 볼 수 없는지 확인한다.
  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("이용자는 조회할 수 없다")
  void search_forbiddenForUser() throws Exception {
    mockMvc.perform(get(PATH)).andExpect(status().isForbidden());
  }
}
