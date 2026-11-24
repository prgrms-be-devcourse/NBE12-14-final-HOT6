package com.back.nbe1214finalhot6.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.dto.WithdrawalPreviewResponse;
import com.back.nbe1214finalhot6.domain.auth.dto.WithdrawalResult;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.service.WithdrawalService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WithdrawalController.class)
@Import(SecurityConfig.class)
@WithAuthUser(userId = 7L) // 로그인한 회원으로 호출한다. 내 지갑은 setUp 에서 70번으로 정한다
class WithdrawalControllerTest {

  private static final String BODY = "{\"expectedRefundAmount\": 50000}";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private WithdrawalService withdrawalService;
  @MockitoBean private OwnedResources ownedResources;

  @BeforeEach
  void setUp() {
    given(ownedResources.walletIdOf(any())).willReturn(70L);
  }

  // 목적: 미리보기가 토큰의 회원과 그 회원의 지갑으로 서비스를 부르고, 응답 필드가 그대로 나가는지 확인한다.
  @Test
  @DisplayName("미리보기는 내 지갑으로 조회하고 환불 예정액·소멸 예정을 반환한다")
  void preview() throws Exception {
    given(withdrawalService.preview(7L, 70L))
        .willReturn(new WithdrawalPreviewResponse(50_000L, 2, 1_500L, -200L));

    mockMvc
        .perform(post("/api/users/withdraw"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.refundAmount").value(50_000))
        .andExpect(jsonPath("$.data.pendingCashbackCount").value(2))
        .andExpect(jsonPath("$.data.pendingCashbackAmount").value(1_500))
        .andExpect(jsonPath("$.data.cashbackBalance").value(-200));
  }

  @Test
  @DisplayName("실행은 Idempotency-Key 와 확인 금액을 서비스에 그대로 넘기고 결과를 반환한다")
  void settle() throws Exception {
    String key = UUID.randomUUID().toString();
    LocalDateTime at = LocalDateTime.of(2026, 11, 24, 9, 0);
    given(withdrawalService.settle(7L, 70L, 50_000L, key))
        .willReturn(new WithdrawalResult(50_000L, 2, 1_500L, at));

    mockMvc
        .perform(
            post("/api/users/withdraw/settle")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.refundedAmount").value(50_000))
        .andExpect(jsonPath("$.data.canceledEarningCount").value(2))
        .andExpect(jsonPath("$.data.canceledEarningAmount").value(1_500))
        .andExpect(jsonPath("$.data.withdrawnAt").exists());
  }

  // 목적: 본문에 남의 지갑 번호를 넣어도 무시하고 토큰의 회원 지갑(70)으로만 처리하는지 확인한다.
  @Test
  @DisplayName("본문에 남의 walletId 를 넣어도 내 지갑으로 처리한다")
  void walletIdInBodyIsIgnored() throws Exception {
    String key = UUID.randomUUID().toString();
    given(withdrawalService.settle(anyLong(), anyLong(), anyLong(), anyString()))
        .willReturn(new WithdrawalResult(0L, 0, 0L, LocalDateTime.of(2026, 11, 24, 9, 0)));

    mockMvc
        .perform(
            post("/api/users/withdraw/settle")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"walletId\": 99, \"expectedRefundAmount\": 0}"))
        .andExpect(status().isOk());

    verify(withdrawalService).settle(7L, 70L, 0L, key);
  }

  @Test
  @DisplayName("확인 금액이 없거나 음수이면 400 이고 서비스를 부르지 않는다")
  void invalidAmount() throws Exception {
    String key = UUID.randomUUID().toString();
    for (String body : new String[] {"{}", "{\"expectedRefundAmount\": -1}"}) {
      mockMvc
          .perform(
              post("/api/users/withdraw/settle")
                  .header("Idempotency-Key", key)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("COM-001"));
    }
    verify(withdrawalService, never()).settle(anyLong(), anyLong(), anyLong(), anyString());
  }

  @Test
  @DisplayName("Idempotency-Key 가 없으면 400(COM-002), UUID 형식이 아니면 400(COM-001)이다")
  void invalidKey() throws Exception {
    mockMvc
        .perform(
            post("/api/users/withdraw/settle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-002"));

    mockMvc
        .perform(
            post("/api/users/withdraw/settle")
                .header("Idempotency-Key", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));

    verify(withdrawalService, never()).settle(anyLong(), anyLong(), anyLong(), anyString());
  }

  // 목적: 금액 변경(AUTH-008)·점주 탈퇴 불가(AUTH-007)가 서비스에서 던져지면 409 와 코드가 그대로 나가는지 확인한다.
  @Test
  @DisplayName("금액이 바뀌었으면 409(AUTH-008), 점주·운영자면 409(AUTH-007)로 그대로 내려간다")
  void errorsPassThrough() throws Exception {
    String key = UUID.randomUUID().toString();
    given(withdrawalService.settle(7L, 70L, 50_000L, key))
        .willThrow(new BusinessException(AuthErrorCode.WITHDRAW_AMOUNT_CHANGED));
    mockMvc
        .perform(
            post("/api/users/withdraw/settle")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH-008"));

    given(withdrawalService.preview(7L, 70L))
        .willThrow(new BusinessException(AuthErrorCode.WITHDRAW_NOT_ALLOWED));
    mockMvc
        .perform(post("/api/users/withdraw"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH-007"));
  }
}
