package com.back.nbe1214finalhot6.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 가입 → 로그인 → 탈퇴 미리보기 → 탈퇴 실행 → 다시 로그인을 실제 보안 설정과 DB 로 끝까지 확인한다.
 *
 * <p>탈퇴는 환불·상태 변경을 한 트랜잭션으로 커밋하므로 테스트 전체를 트랜잭션으로 감싸지 않고, 끝나면 만든 행을 지운다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class WithdrawalFlowTest {

  private static final String EMAIL = "wdflow@devpay.com";
  private static final String PASSWORD = "Password1!";

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired UserRepository userRepository;
  @Autowired WalletRepository walletRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired AccountTransactionRepository accountTransactionRepository;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("delete from wallet_transaction");
    jdbcTemplate.update("delete from refund");
    accountTransactionRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
    userRepository.deleteAll();
  }

  private ResultActions authed(String path, String token, String key, String body)
      throws Exception {
    var request = post(path).header("Authorization", "Bearer " + token);
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions login() throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(EMAIL, PASSWORD)));
  }

  // 목적: 미리보기 → 실행 → 재로그인 거절까지 실제 흐름이 이어지는지, 환불 원장이 남는지 확인한다.
  @Test
  @DisplayName("가입·로그인한 회원이 미리보기 후 탈퇴하면 환불되고, 다시 로그인할 수 없다")
  void withdrawFlow() throws Exception {
    mockMvc
        .perform(
            post("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "홍길동", "email": "%s", "password": "%s",
                     "phone": "01055556666", "pin": "123456"}
                    """
                        .formatted(EMAIL, PASSWORD)))
        .andExpect(status().isCreated());
    String token = JsonPath.read(login().andReturn().getResponse().getContentAsString(), "$.data.accessToken");
    jdbcTemplate.update("update wallet set balance = 50000");
    Long accountId = jdbcTemplate.queryForObject("select id from account", Long.class);
    long accountStart = jdbcTemplate.queryForObject("select balance from account", Long.class);

    // 1) 미리보기: 금액만 알려 주고 아무것도 바꾸지 않는다
    authed("/api/users/withdraw", token, null, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.refundAmount").value(50_000))
        .andExpect(jsonPath("$.data.pendingCashbackCount").value(0));
    assertThat(jdbcTemplate.queryForObject("select status from users", String.class))
        .isEqualTo("ACTIVE");

    // 2) 확인한 금액과 다르면 거절
    authed("/api/users/withdraw/settle", token, UUID.randomUUID().toString(), "{\"expectedRefundAmount\": 40000}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH-008"));

    // 3) 확인한 금액으로 실행
    authed("/api/users/withdraw/settle", token, UUID.randomUUID().toString(), "{\"expectedRefundAmount\": 50000}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.refundedAmount").value(50_000));

    assertThat(jdbcTemplate.queryForObject("select status from users", String.class))
        .isEqualTo("WITHDRAWN");
    assertThat(jdbcTemplate.queryForObject("select status from wallet", String.class))
        .isEqualTo("WITHDRAWN");
    assertThat(jdbcTemplate.queryForObject("select balance from wallet", Long.class)).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "select balance from account where id = ?", Long.class, accountId))
        .isEqualTo(accountStart + 50_000L);

    // 4) 같은 요청을 다시 보내도 한 번만 반영된다
    authed("/api/users/withdraw/settle", token, UUID.randomUUID().toString(), "{\"expectedRefundAmount\": 0}")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-004"));
    assertThat(jdbcTemplate.queryForObject("select count(*) from refund", Integer.class))
        .isEqualTo(1);

    // 5) 탈퇴한 회원은 다시 로그인할 수 없다
    login().andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-004"));
  }
}
