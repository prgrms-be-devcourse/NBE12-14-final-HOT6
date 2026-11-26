package com.back.nbe1214finalhot6.domain.payment.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.service.MerchantPaymentQueryService;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentFacade;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.perf.ConcurrentRunner;
import com.back.nbe1214finalhot6.support.perf.ConcurrentRunner.Task;
import com.back.nbe1214finalhot6.support.perf.PerfFixture;
import com.back.nbe1214finalhot6.support.perf.PerfFixture.CompletedPayment;
import com.back.nbe1214finalhot6.support.perf.PerfReport;
import com.back.nbe1214finalhot6.support.perf.PerfReport.Environment;
import com.back.nbe1214finalhot6.support.perf.PerfRounds;
import com.back.nbe1214finalhot6.support.perf.PerfRounds.Round;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * S6 커넥션 풀 고갈: 락을 기다리는 결제가 커넥션을 붙잡으면, 락과 상관없는 조회까지 느려지나.
 *
 * <p>풀을 2 로 줄여 상황을 작게 재현한다. 지갑 락을 3초 쥔 트랜잭션(커넥션 1개) + 그 지갑으로 결제 3건(락을 기다리며 커넥션을 붙잡음) + 사장님 결제
 * 목록 조회 5건(지갑 락과 무관). 비교를 위해 같은 조회만 돌린 경우(reads-only)도 잰다. 조회가 느려졌다면 락 대기가 풀을 통해 다른 기능으로 번진
 * 것이고, 락 대기 상한·트랜잭션 타임아웃(#82)이 필요한 이유다.
 */
@Tag("perf")
@IntegrationTest
@Import(PerfFixture.class)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=2")
class PaymentPoolExhaustionPerfTest {

  private static final long AMOUNT = 10_000L;
  private static final long HOLD_MS = 3_000;
  private static final int PAYMENTS = 3;
  private static final int READS = 5;

  @Autowired PerfFixture fixture;
  @Autowired PaymentFacade paymentFacade;
  @Autowired MerchantPaymentQueryService merchantPaymentQueryService;
  @Autowired DataSource dataSource;

  Environment env;

  @BeforeEach
  void setUp() {
    fixture.ensureBudget();
    env = PerfReport.environment(dataSource);
  }

  @Test
  @DisplayName("S6 풀 2개에서 락 대기 결제가 커넥션을 잡으면 무관한 결제 목록 조회도 밀린다")
  void S6_풀_고갈() {
    String scenario = PerfRounds.scenarioName("S6-pool-exhaustion");
    int rounds = Math.min(PerfRounds.rounds(), 3);
    List<String> violations = new ArrayList<>();

    // 비교 기준: 락·결제 없이 조회만
    violations.addAll(
        PerfRounds.measure(
            scenario, "reads-only", Map.of("reads", READS), rounds, true,
            Duration.ofSeconds(60), env, () -> prepare(false)));

    Map<String, Object> conditions = new LinkedHashMap<>();
    conditions.put("holdMillis", HOLD_MS);
    conditions.put("payments", PAYMENTS);
    conditions.put("reads", READS);
    violations.addAll(
        PerfRounds.measure(
            scenario, "hold3s", conditions, rounds, false,
            Duration.ofSeconds(90), env, () -> prepare(true)));

    assertThat(violations).as("불변 조건 위반").isEmpty();
  }

  private Round prepare(boolean withLockHolder) {
    // 조회할 가맹점에 결제 내역 1건을 만들어 둔다
    CompletedPayment existing = fixture.completedPayment(AMOUNT);
    Long merchantId = existing.merchantId();
    Long walletId = fixture.wallet(AMOUNT * PAYMENTS);
    List<Task> tasks = new ArrayList<>();
    if (withLockHolder) {
      tasks.add(Task.of("holder", () -> fixture.holdWalletLock(walletId, HOLD_MS)));
      for (int i = 0; i < PAYMENTS; i++) {
        PaymentRequest request = fixture.payRequest(walletId, fixture.qr(merchantId, AMOUNT));
        String key = PerfFixture.newKey();
        tasks.add(Task.delayed("pay", 200, () -> paymentFacade.pay(key, request).status().name()));
      }
    }
    for (int i = 0; i < READS; i++) {
      tasks.add(
          Task.delayed(
              "read",
              withLockHolder ? 500 : 0,
              () -> {
                merchantPaymentQueryService.get(merchantId, null, null, 0, 20);
                return ConcurrentRunner.OK;
              }));
    }
    return Round.of(
        tasks,
        r -> {
          long readOk = r.counts("read").getOrDefault(ConcurrentRunner.OK, 0L);
          long completed =
              withLockHolder ? fixture.payments(walletId, PaymentStatus.COMPLETED) : PAYMENTS;
          return PerfRounds.checks(
              readOk == READS && completed == PAYMENTS,
              "readOk", readOk, "paymentsCompleted", completed,
              "readP50Ms", r.latency("read").p50Ms(), "readMaxMs", r.latency("read").maxMs());
        });
  }
}
