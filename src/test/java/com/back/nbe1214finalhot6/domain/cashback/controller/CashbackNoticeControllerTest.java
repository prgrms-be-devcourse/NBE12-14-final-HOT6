package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeType;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackNoticeService;
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

// 로그인한 사용자(허용 역할)로 호출해서 보안 설정을 거쳐도 통과하는지 함께 확인한다.
@WebMvcTest(CashbackNoticeController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "USER")
class CashbackNoticeControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private CashbackNoticeService noticeService;

  // 목적: 로그인한 사용자가 조회하고 공지 종류·문구·적용 시작일·중단 예정일이 공통 응답 형식으로 내려가며 종류·페이지 파라미터가 서비스에 전달되는지 확인한다.
  @Test
  @DisplayName("공지 목록은 로그인한 사용자가 조회하고 파라미터를 서비스에 넘긴다")
  void list_ok() throws Exception {
    NoticeResponse notice =
        new NoticeResponse(
            3L,
            NoticeType.POLICY_CHANGE,
            "적립률 변경 안내",
            "12월 1일부터 적립률이 5%로 바뀌어요.",
            LocalDate.of(2026, 12, 1),
            null,
            LocalDateTime.of(2026, 11, 18, 9, 0),
            new BigDecimal("0.05"),
            new BigDecimal("0.10"),
            null,
            null,
            null);
    given(noticeService.list(NoticeType.POLICY_CHANGE, 1, 5))
        .willReturn(new PageResponse<>(List.of(notice), 1, 5, 6L, 2));

    mockMvc
        .perform(
            get("/api/notices").param("type", "POLICY_CHANGE").param("page", "1").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalElements").value(6))
        .andExpect(jsonPath("$.data.content[0].type").value("POLICY_CHANGE"))
        .andExpect(jsonPath("$.data.content[0].content").value("12월 1일부터 적립률이 5%로 바뀌어요."))
        .andExpect(jsonPath("$.data.content[0].effectiveFrom").value("2026-12-01"))
        .andExpect(jsonPath("$.data.content[0].stopAt").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].rate").value(0.05))
        .andExpect(jsonPath("$.data.content[0].previousRate").value(0.10));
  }

  // 목적: 종류·페이지를 생략하면 전체, 첫 페이지, 기본 크기 20 으로 조회하는지 확인한다.
  @Test
  @DisplayName("파라미터를 생략하면 전체 첫 페이지를 기본 크기로 조회한다")
  void list_defaults() throws Exception {
    given(noticeService.list(null, 0, 20)).willReturn(new PageResponse<>(List.of(), 0, 20, 0L, 0));

    mockMvc
        .perform(get("/api/notices"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty());

    verify(noticeService).list(null, 0, 20);
  }

  // 목적: 알 수 없는 종류나 범위를 벗어난 페이지 번호·크기는 400 으로 걸러지는지 확인한다.
  @Test
  @DisplayName("잘못된 종류와 페이지 파라미터는 400")
  void list_invalidParameters() throws Exception {
    mockMvc.perform(get("/api/notices").param("type", "NOPE")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/notices").param("page", "-1")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/notices").param("size", "0")).andExpect(status().isBadRequest());
  }

  // 목적: 홈 공지가 공통 응답 형식으로 내려가는지 확인한다.
  @Test
  @DisplayName("홈 공지를 조회한다")
  void home_ok() throws Exception {
    given(noticeService.listForHome())
        .willReturn(
            List.of(
                new NoticeResponse(
                    7L,
                    NoticeType.LIMIT_CHANGE,
                    "한도 변경 안내",
                    "본문",
                    LocalDate.of(2026, 12, 1),
                    null,
                    LocalDateTime.of(2026, 11, 18, 9, 0),
                    null,
                    null,
                    "CASHBACK_USER_MONTHLY_LIMIT",
                    50_000L,
                    40_000L)));

    mockMvc
        .perform(get("/api/notices/home"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].title").value("한도 변경 안내"))
        .andExpect(jsonPath("$.data[0].newValue").value(40_000));
  }
}
