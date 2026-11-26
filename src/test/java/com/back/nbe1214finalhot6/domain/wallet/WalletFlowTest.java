package com.back.nbe1214finalhot6.domain.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
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
 * 가입 → 로그인 → 내 지갑 조회·충전·환불을, 실제 보안 설정과 DB 로 끝까지 확인한다.
 *
 * <p>테스트 전체를 한 트랜잭션으로 감싸서(끝나면 롤백) 따로 정리하지 않는다. 한도는 운영자 설정이 없을 때의 기본값(보유 200만원, 월 충전 100만원)이고, 가입하면
 * 모의계좌에 500만원이 들어 있다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class WalletFlowTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtProvider jwtProvider;
  @Autowired WalletRepository walletRepository;

  // 목적: 가입한 회원이 지갑을 조회하고, 충전·환불하면 잔액이 맞게 바뀌는지, 같은 멱등키는 한 번만 반영되는지 확인한다.
  @Test
  @DisplayName("가입 → 조회 → 충전 → 같은 키 재요청 → 환불 → 잔액 부족 환불 거절")
  void chargeAndRefundFlow() throws Exception {
    String token = signupAndLogin("w1@devpay.com", "01088880001");

    // 처음 지갑은 비어 있다
    assertWallet(token, 0, 0);

    // 10만원 충전하면 데브머니와 이번 달 충전액이 오른다
    String chargeKey = UUID.randomUUID().toString();
    MvcResult charged = charge(token, chargeKey, 100_000).andExpect(status().isOk()).andReturn();
    int chargeId = JsonPath.read(charged.getResponse().getContentAsString(), "$.data.chargeId");
    assertWallet(token, 100_000, 100_000);

    // 같은 멱등키로 다시 요청하면 한 번만 반영되고 같은 응답이 온다
    charge(token, chargeKey, 100_000)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.chargeId").value(chargeId));
    assertWallet(token, 100_000, 100_000);

    // 3만원 환불하면 데브머니만 줄고, 이번 달 충전액은 줄지 않는다
    refund(token, UUID.randomUUID().toString(), 30_000).andExpect(status().isOk());
    assertWallet(token, 70_000, 100_000);

    // 잔액보다 많이 환불하면 거절하고 잔액은 그대로다
    refund(token, UUID.randomUUID().toString(), 80_000)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WAL-004"));
    assertWallet(token, 70_000, 100_000);
  }

  // 목적: 월 충전 한도(기본 100만원)를 넘으면 WAL-007 로 거절하고 잔액이 바뀌지 않는지 확인한다.
  @Test
  @DisplayName("월 충전 한도를 넘으면 409 WAL-007 이고 잔액은 그대로다")
  void monthlyChargeLimit() throws Exception {
    String token = signupAndLogin("w2@devpay.com", "01088880002");

    charge(token, UUID.randomUUID().toString(), 1_000_000).andExpect(status().isOk());
    charge(token, UUID.randomUUID().toString(), 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WAL-007"));

    assertWallet(token, 1_000_000, 1_000_000);
  }

  // 목적: 남의 지갑을 가리킬 방법이 없는지 확인한다. 요청에 남의 walletId 를 넣어도 항상 내 지갑이 나와야 한다.
  @Test
  @DisplayName("요청에 남의 walletId 를 넣어도 내 지갑만 보이고, 남의 지갑은 바뀌지 않는다")
  void cannotTouchOthersWallet() throws Exception {
    String aToken = signupAndLogin("w3@devpay.com", "01088880003");
    String bToken = signupAndLogin("w4@devpay.com", "01088880004");
    charge(aToken, UUID.randomUUID().toString(), 100_000).andExpect(status().isOk());
    long aWalletId =
        walletRepository.findByUserId(jwtProvider.parse(aToken).userId()).orElseThrow().getId();

    // B 가 A 의 walletId 를 붙여 조회해도 B 의 지갑(0원)이 나온다
    mockMvc
        .perform(
            get("/api/wallet")
                .param("walletId", String.valueOf(aWalletId))
                .header("Authorization", "Bearer " + bToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.devMoney").value(0));

    // B 가 A 의 walletId 를 본문에 넣어 충전해도 B 의 지갑만 오르고 A 는 그대로다
    mockMvc
        .perform(
            post("/api/wallet/charges")
                .header("Authorization", "Bearer " + bToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 50000, \"walletId\": %d}".formatted(aWalletId)))
        .andExpect(status().isOk());
    assertWallet(bToken, 50_000, 50_000);
    assertWallet(aToken, 100_000, 100_000);
  }

  // 목적: 토큰이 없으면 401, 운영자 토큰은 시민 기능이라 403 인지 실제 보안 설정으로 확인한다.
  @Test
  @DisplayName("토큰이 없으면 401, 운영자는 403 이다")
  void authAndRoles() throws Exception {
    mockMvc.perform(get("/api/wallet")).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COM-005"));

    String adminToken = jwtProvider.createAccessToken(1L, UserRole.ADMIN);
    mockMvc
        .perform(get("/api/wallet").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COM-008"));
  }

  private void assertWallet(String token, long devMoney, long chargedThisMonth) throws Exception {
    mockMvc
        .perform(get("/api/wallet").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.devMoney").value(devMoney))
        .andExpect(jsonPath("$.data.chargedThisMonth").value(chargedThisMonth));
  }

  private ResultActions charge(String token, String key, long amount) throws Exception {
    return mockMvc.perform(
        post("/api/wallet/charges")
            .header("Authorization", "Bearer " + token)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\": %d}".formatted(amount)));
  }

  private ResultActions refund(String token, String key, long amount) throws Exception {
    return mockMvc.perform(
        post("/api/wallet/refunds")
            .header("Authorization", "Bearer " + token)
            .header("Idempotency-Key", key)
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
                    .content(
                        "{\"email\": \"%s\", \"password\": \"Password1!\"}".formatted(email)))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
  }
}
