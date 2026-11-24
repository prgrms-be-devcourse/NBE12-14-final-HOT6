package com.back.nbe1214finalhot6.domain.wallet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 → 로그인 → 모의계좌 잔액·내역 조회 → 충전·환불 뒤 다시 조회를, 실제 보안 설정과 DB 로 끝까지 확인한다.
 *
 * <p>테스트 전체를 한 트랜잭션으로 감싸서(끝나면 롤백) 따로 정리하지 않는다. 가입하면 모의계좌에 200만원이 들어 있다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class AccountFlowTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtProvider jwtProvider;

  // 목적: 가입 직후 잔액·가입 보너스 내역이 보이고, 충전하면 계좌가 줄고 환불하면 늘며 내역에 맞게 쌓이는지 확인한다.
  @Test
  @DisplayName("가입 보너스 → 충전(계좌 −) → 환불(계좌 +)이 잔액과 내역에 그대로 나온다")
  void accountFlow() throws Exception {
    String token = signupAndLogin("ac1@devpay.com", "01099990001");

    // 가입 직후: 200만원, 내역은 가입 보너스 1줄
    balance(token, 2_000_000);
    getAs(token, "/api/wallet/account/transactions")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].type").value("SIGNUP_BONUS"))
        .andExpect(jsonPath("$.data.content[0].amount").value(2_000_000))
        .andExpect(jsonPath("$.data.content[0].balanceAfter").value(2_000_000))
        .andExpect(jsonPath("$.data.content[0].refKey").doesNotExist());

    // 10만원 충전: 계좌에서 빠진다
    MvcResult charged =
        walletPost(token, "/api/wallet/charges", 100_000).andExpect(status().isOk()).andReturn();
    int chargeId = JsonPath.read(charged.getResponse().getContentAsString(), "$.data.chargeId");
    balance(token, 1_900_000);
    getAs(token, "/api/wallet/account/transactions")
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[0].type").value("CHARGE"))
        .andExpect(jsonPath("$.data.content[0].amount").value(-100_000))
        .andExpect(jsonPath("$.data.content[0].balanceAfter").value(1_900_000))
        .andExpect(jsonPath("$.data.content[0].refKey").value(chargeId));

    // 3만원 환불: 계좌로 돌아온다
    MvcResult refunded =
        walletPost(token, "/api/wallet/refunds", 30_000).andExpect(status().isOk()).andReturn();
    int refundId = JsonPath.read(refunded.getResponse().getContentAsString(), "$.data.refundId");
    balance(token, 1_930_000);
    getAs(token, "/api/wallet/account/transactions")
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.content[0].type").value("REFUND"))
        .andExpect(jsonPath("$.data.content[0].amount").value(30_000))
        .andExpect(jsonPath("$.data.content[0].balanceAfter").value(1_930_000))
        .andExpect(jsonPath("$.data.content[0].refKey").value(refundId))
        .andExpect(jsonPath("$.data.content[2].type").value("SIGNUP_BONUS"));

    // 페이지 크기 1이면 한 줄씩
    mockMvc
        .perform(
            get("/api/wallet/account/transactions")
                .param("size", "1")
                .param("page", "1")
                .header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].type").value("CHARGE"))
        .andExpect(jsonPath("$.data.totalPages").value(3));
  }

  // 목적: 남의 계좌를 가리킬 방법이 없는지 확인한다. 요청에 남의 번호를 넣어도 항상 내 계좌가 나와야 한다.
  @Test
  @DisplayName("요청에 남의 userId·accountId 를 넣어도 내 계좌만 보이고, 다른 회원의 충전은 섞이지 않는다")
  void cannotSeeOthersAccount() throws Exception {
    String aToken = signupAndLogin("ac2@devpay.com", "01099990002");
    String bToken = signupAndLogin("ac3@devpay.com", "01099990003");
    walletPost(aToken, "/api/wallet/charges", 500_000).andExpect(status().isOk());
    long aUserId = jwtProvider.parse(aToken).userId();

    // B 가 A 의 번호를 붙여도 B 의 계좌(200만원, 내역 1줄)가 나온다
    mockMvc
        .perform(
            get("/api/wallet/account")
                .param("userId", String.valueOf(aUserId))
                .header("Authorization", "Bearer " + bToken))
        .andExpect(jsonPath("$.data.balance").value(2_000_000));
    mockMvc
        .perform(
            get("/api/wallet/account/transactions")
                .param("accountId", "1")
                .param("userId", String.valueOf(aUserId))
                .header("Authorization", "Bearer " + bToken))
        .andExpect(jsonPath("$.data.totalElements").value(1));
    balance(aToken, 1_500_000);
  }

  // 목적: 토큰이 없으면 401, 운영자 토큰은 시민 기능이라 403 인지 실제 보안 설정으로 확인한다.
  @Test
  @DisplayName("토큰이 없으면 401, 운영자는 403 이다")
  void authAndRoles() throws Exception {
    mockMvc
        .perform(get("/api/wallet/account"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COM-005"));

    String adminToken = jwtProvider.createAccessToken(1L, UserRole.ADMIN);
    mockMvc
        .perform(get("/api/wallet/account/transactions").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COM-008"));
  }

  private void balance(String token, long expected) throws Exception {
    getAs(token, "/api/wallet/account")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.balance").value(expected));
  }

  private ResultActions getAs(String token, String path) throws Exception {
    return mockMvc.perform(get(path).header("Authorization", "Bearer " + token));
  }

  private ResultActions walletPost(String token, String path, long amount) throws Exception {
    return mockMvc.perform(
        post(path)
            .header("Authorization", "Bearer " + token)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\": %d}".formatted(amount)));
  }

  private String signupAndLogin(String email, String phone) throws Exception {
    mockMvc
        .perform(
            post("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "홍길동", "email": "%s", "password": "Password1!",
                     "phone": "%s", "pin": "123456"}
                    """
                        .formatted(email, phone)))
        .andExpect(status().isCreated());
    MvcResult login =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"%s\", \"password\": \"Password1!\"}".formatted(email)))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
  }
}
