package com.back.nbe1214finalhot6.domain.payment.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentFacade;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.perf.ConcurrentRunner.Task;
import com.back.nbe1214finalhot6.support.perf.PerfFixture;
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
 * S5 락 보유 시간 증가: 누군가 지갑 행 락을 오래 쥐면 같은 지갑의 결제는 얼마나 기다리나.
 *
 * <p>지금 코드에는 락 대기 상한(lock_timeout)도 트랜잭션 타임아웃도 없다. 그래서 결제의 응답 시간은 "락을 쥔 시간"을 그대로 따라간다는 것을 1초,
 * 5초, 30초로 보여 준다. 개선(#82, 락 대기 5초) 뒤에는 30초 보유에서도 결제가 약 5초에 PAY-005 로 끝나야 한다.
 */
@Tag("perf")
@IntegrationTest
@Import(PerfFixture.class)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=10")
class PaymentLockWaitPerfTest {

  private static final long AMOUNT = 10_000L;
  private static final int PAYMENTS = 5;
  private static final long PAY_START_DELAY_MS = 200; // 락을 쥔 쪽이 먼저 잠그도록 결제는 조금 늦게 출발한다

  @Autowired PerfFixture fixture;
  @Autowired PaymentFacade paymentFacade;
  @Autowired DataSource dataSource;

  Environment env;

  @BeforeEach
  void setUp() {
    fixture.ensureBudget();
    fixture.completedPayment(AMOUNT); // 커넥션 생성·첫 실행 비용을 측정 밖에서 치른다
    env = PerfReport.environment(dataSource);
  }

  @Test
  @DisplayName("S5 지갑 락을 1·5·30초 쥐면 같은 지갑 결제는 그만큼 기다린다 (타임아웃 없음)")
  void S5_락_보유() {
    String scenario = PerfRounds.scenarioName("S5-lock-hold");
    List<String> violations = new ArrayList<>();
    long[] holds = {1_000, 5_000, 30_000};
    for (long hold : holds) {
      int rounds = hold >= 30_000 ? 1 : Math.min(PerfRounds.rounds(), 3); // 30초는 1라운드만
      Map<String, Object> conditions = new LinkedHashMap<>();
      conditions.put("holdMillis", hold);
      conditions.put("payments", PAYMENTS);
      conditions.put("payStartDelayMillis", PAY_START_DELAY_MS);
      violations.addAll(
          PerfRounds.measure(
              scenario,
              "hold" + hold / 1000 + "s",
              conditions,
              rounds,
              false,
              Duration.ofMillis(hold + 60_000),
              env,
              () -> prepare(hold)));
    }
    assertThat(violations).as("불변 조건 위반").isEmpty();
  }

  private Round prepare(long hold) {
    Long walletId = fixture.wallet(AMOUNT * PAYMENTS);
    Long merchantId = fixture.merchant();
    List<Task> tasks = new ArrayList<>();
    tasks.add(Task.of("holder", () -> fixture.holdWalletLock(walletId, hold)));
    for (int i = 0; i < PAYMENTS; i++) {
      PaymentRequest request = fixture.payRequest(walletId, fixture.qr(merchantId, AMOUNT));
      String key = PerfFixture.newKey();
      tasks.add(
          Task.delayed(
              "pay", PAY_START_DELAY_MS, () -> paymentFacade.pay(key, request).status().name()));
    }
    return Round.of(
        tasks,
        r -> {
          long completed = fixture.payments(walletId, PaymentStatus.COMPLETED);
          long balance = fixture.balance(walletId);
          double minPayMs = r.latency("pay").minMs();
          return PerfRounds.checks(
              completed == PAYMENTS && balance == 0,
              "completed", completed, "balance", balance,
              // 기대: 가장 빨리 끝난 결제도 (락 보유 - 출발 지연) 이상 기다린다
              "minPayMs", minPayMs, "expectedMinWaitMs", hold - PAY_START_DELAY_MS);
        });
  }
}
