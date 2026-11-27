package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackNoticeService;
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

@WebMvcTest(CashbackNoticeAdminController.class)
@Import(SecurityConfig.class)
class CashbackNoticeAdminControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private CashbackNoticeService noticeService;

  // 목적: 운영자는 기본 페이지로 목록을 조회할 수 있는지 확인한다.
  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("운영자는 공지 목록을 조회한다")
  void list_ok() throws Exception {
    given(noticeService.listForAdmin(0, 20))
        .willReturn(new PageResponse<>(List.of(), 0, 20, 0L, 0));

    mockMvc
        .perform(get("/api/admin/notices"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty());
  }

  // 목적: 제목이 비었으면 400 인지 확인한다.
  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("빈 제목은 400")
  void update_invalid() throws Exception {
    mockMvc
        .perform(
            patch("/api/admin/notices/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \",\"content\":\"본문\",\"showOnHome\":false}"))
        .andExpect(status().isBadRequest());
  }

  // 목적: 이용자 역할은 운영자 공지 API 를 쓸 수 없는지 확인한다.
  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("이용자는 403")
  void forbidden() throws Exception {
    mockMvc.perform(get("/api/admin/notices")).andExpect(status().isForbidden());
  }
}
