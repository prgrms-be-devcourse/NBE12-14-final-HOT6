package com.back.nbe1214finalhot6.domain.auth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
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
  private static final String PIN = "123456";
  private static final String WRONG_PIN = "000000";

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired UserRepository userRepository;
  @Autowired WalletRepository walletRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired AccountTransactionRepository accountTransactionRepository;
  @Autowired CashbackEarningRepository earningRepository;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("delete from cashback_earning");
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

  // 가입하고 로그인해 액세스 토큰을 돌려준다
  private String signupAndLogin() throws Exception {
    mockMvc
        .perform(
            post("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "홍길동", "email": "%s", "password": "%s",
                     "phone": "01055556666", "pin": "%s"}
                    """
                        .formatted(EMAIL, PASSWORD, PIN)))
        .andExpect(status().isCreated());
    return JsonPath.read(
        login().andReturn().getResponse().getContentAsString(), "$.data.accessToken");
  }

  // 탈퇴 실행 요청. 매번 새 멱등키를 쓴다
  private ResultActions settle(String token, long expected, String pin) throws Exception {
    return settle(token, UUID.randomUUID().toString(), expected, pin);
  }

  private ResultActions settle(String token, String key, long expected, String pin)
      throws Exception {
    return authed(
        "/api/users/withdraw/settle",
        token,
        key,
        "{\"expectedRefundAmount\": %d, \"pin\": \"%s\"}".formatted(expected, pin));
  }

  // 아직 지급되지 않은 적립 예정 캐시백 한 건을 만든다
  private void savePendingEarning(Long walletId) {
    LocalDateTime now = LocalDateTime.of(2026, 11, 24, 9, 0);
    earningRepository.save(
        CashbackEarning.pending(
            1L,
            walletId,
            10_000L,
            now,
            new BigDecimal("0.0500"),
            1L,
            500L,
            now.plusDays(3),
            LocalDate.of(2026, 11, 1),
            now));
  }

  // 목적: 미리보기 → 실행 → 재로그인 거절까지 실제 흐름이 이어지는지, 환불 원장이 남는지 확인한다.
  @Test
  @DisplayName("가입·로그인한 회원이 미리보기 후 탈퇴하면 환불되고, 다시 로그인할 수 없다")
  void withdrawFlow() throws Exception {
    String token = signupAndLogin();
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
    settle(token, 40_000L, PIN)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH-008"));

    // 3) 확인한 금액으로 실행
    settle(token, 50_000L, PIN)
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
    settle(token, 0L, PIN)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-004"));
    assertThat(jdbcTemplate.queryForObject("select count(*) from refund", Integer.class))
        .isEqualTo(1);

    // 5) 같은 토큰으로 다른 API 를 불러도 즉시 막힌다(토큰 만료를 기다리지 않음)
    mockMvc
        .perform(get("/api/merchants").param("name", "x").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-004"));

    // 6) 탈퇴한 회원은 다시 로그인할 수 없다
    login().andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-004"));
  }

  // 목적: PIN 없이 또는 틀린 PIN 으로는 아무것도 바뀌지 않고, 5번 틀리면 잠겨 맞는 PIN 으로도 탈퇴되지 않는지 확인한다.
  @Test
  @DisplayName("PIN 이 없거나 틀리면 탈퇴되지 않고, 5번 틀리면 잠겨 맞는 PIN 도 거절한다")
  void wrongPinChangesNothing() throws Exception {
    String token = signupAndLogin();
    jdbcTemplate.update("update wallet set balance = 50000");
    long accountStart = jdbcTemplate.queryForObject("select balance from account", Long.class);
    savePendingEarning(jdbcTemplate.queryForObject("select id from wallet", Long.class));

    authed(
            "/api/users/withdraw/settle",
            token,
            UUID.randomUUID().toString(),
            "{\"expectedRefundAmount\": 50000}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));

    for (int attempt = 1; attempt <= 4; attempt++) {
      settle(token, 50_000L, WRONG_PIN)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("AUTH-005"));
      // 실패 횟수는 요청이 거절돼도 남는다
      assertThat(jdbcTemplate.queryForObject("select pin_fail_count from users", Integer.class))
          .isEqualTo(attempt);
    }
    settle(token, 50_000L, WRONG_PIN)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH-006"));
    settle(token, 50_000L, PIN)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH-006"));

    assertThat(jdbcTemplate.queryForObject("select status from users", String.class))
        .isEqualTo("ACTIVE");
    assertThat(jdbcTemplate.queryForObject("select status from wallet", String.class))
        .isEqualTo("ACTIVE");
    assertThat(jdbcTemplate.queryForObject("select balance from wallet", Long.class))
        .isEqualTo(50_000L);
    assertThat(jdbcTemplate.queryForObject("select balance from account", Long.class))
        .isEqualTo(accountStart);
    assertThat(jdbcTemplate.queryForObject("select count(*) from refund", Integer.class)).isZero();
    assertThat(jdbcTemplate.queryForObject("select status from cashback_earning", String.class))
        .isEqualTo("PENDING");
  }

  // 목적: 응답을 못 받아 처리 중에 다시 보낸 상황. 한 번만 처리되고 다른 요청은 "이미 탈퇴"로 답해야 화면이 완료로 안내한다.
  @Test
  @DisplayName("같은 요청이 동시에 두 번 와도 환불은 한 번이고, 다른 요청은 이미 탈퇴한 것으로 답한다")
  void sameRequestTwiceAtOnce() throws Exception {
    String token = signupAndLogin();
    jdbcTemplate.update("update wallet set balance = 50000");
    String key = UUID.randomUUID().toString();
    CountDownLatch start = new CountDownLatch(1);
    Callable<MockHttpServletResponse> request =
        () -> {
          start.await();
          return settle(token, key, 50_000L, PIN).andReturn().getResponse();
        };

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<MockHttpServletResponse>> futures =
          List.of(pool.submit(request), pool.submit(request));
      start.countDown();
      List<String> outcomes = new ArrayList<>();
      for (Future<MockHttpServletResponse> future : futures) {
        MockHttpServletResponse response = future.get(30, TimeUnit.SECONDS);
        outcomes.add(
            response.getStatus() == 200
                ? "OK"
                : JsonPath.read(response.getContentAsString(UTF_8), "$.code"));
      }
      assertThat(outcomes).containsOnlyOnce("OK");
      assertThat(outcomes).allMatch(code -> List.of("OK", "AUTH-004", "WAL-002").contains(code));
    } finally {
      pool.shutdownNow();
    }

    assertThat(jdbcTemplate.queryForObject("select count(*) from refund", Integer.class))
        .isEqualTo(1);
  }
}
