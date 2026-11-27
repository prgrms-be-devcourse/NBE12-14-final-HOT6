package com.back.nbe1214finalhot6.domain.cashback.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackBudgetStateLogRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackNoticeRepository;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetStateService;
import com.back.nbe1214finalhot6.support.TestClockConfig;
import com.back.nbe1214finalhot6.support.TestDatabaseInitializer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * 실제 서버에 HTTP 로 연결해 적립 상태 스트림의 권한, 응답 헤더, 실시간 전달을 확인한다. 열린 연결이 DB 연결을 붙잡지 않는지도 함께 본다(테스트 연결 풀은
 * 2개다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseInitializer.class)
@Import(TestClockConfig.class)
class CashbackStatusStreamHttpTest {

  private static final int YEAR = 2026;
  private static final int MONTH = 11;

  @LocalServerPort private int port;
  @Autowired private JwtProvider jwtProvider;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackBudgetStateService stateService;
  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  @Autowired private CashbackBudgetStateLogRepository stateLogRepository;
  @Autowired private CashbackNoticeRepository noticeRepository;

  private final HttpClient client = HttpClient.newHttpClient();

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(YEAR, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(
        YEAR, MONTH, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  @AfterEach
  void cleanUp() {
    client.shutdownNow();
    stateLogRepository.deleteAll();
    noticeRepository.deleteAll();
    monthlyBudgetRepository.deleteAll();
    annualBudgetRepository.deleteAll();
  }

  private HttpResponse<InputStream> open(UserRole role) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/cashback/status/stream"))
            .header("Accept", "text/event-stream")
            .timeout(Duration.ofSeconds(10));
    if (role != null) {
      request.header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L, role));
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
  }

  private static String nextData(BufferedReader reader) throws Exception {
    return CompletableFuture.supplyAsync(
            () -> {
              try {
                String line;
                while ((line = reader.readLine()) != null) {
                  if (line.startsWith("data:")) {
                    return line.substring("data:".length()).trim();
                  }
                }
                return null;
              } catch (IOException e) {
                throw new IllegalStateException(e);
              }
            })
        .get(10, TimeUnit.SECONDS);
  }

  // 목적: 연결 풀(2개)보다 많은 연결을 열어 둔 채 중단해도 모두 실시간으로 받고, 그동안 DB 작업이 막히지 않는지 확인한다.
  @Test
  @DisplayName("열린 연결이 많아도 DB 를 쓸 수 있고 중단이 모든 연결에 전달된다")
  void deliversStopToAllOpenConnections() throws Exception {
    List<BufferedReader> readers = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      HttpResponse<InputStream> response = open(UserRole.USER);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("content-type").orElse(""))
          .startsWith("text/event-stream");
      assertThat(response.headers().firstValue("cache-control"))
          .hasValue("no-cache, no-transform");
      assertThat(response.headers().firstValue("x-accel-buffering")).hasValue("no");
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
      assertThat(nextData(reader)).isEqualTo("{\"paused\":false}");
      readers.add(reader);
    }

    stateService.emergencyStopManually(YEAR, MONTH);

    for (BufferedReader reader : readers) {
      assertThat(nextData(reader)).isEqualTo("{\"paused\":true}");
    }
  }

  // 목적: 이용자만 연결할 수 있는지 확인한다.
  @Test
  @DisplayName("이용자는 연결할 수 있고 점주·운영자와 비로그인은 거절한다")
  void authorization() throws Exception {
    assertThat(open(UserRole.USER).statusCode()).isEqualTo(200);
    assertThat(open(UserRole.OWNER).statusCode()).isEqualTo(403);
    assertThat(open(UserRole.ADMIN).statusCode()).isEqualTo(403);
    assertThat(open(null).statusCode()).isEqualTo(401);
  }
}
