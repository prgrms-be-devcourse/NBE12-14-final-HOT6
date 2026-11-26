package com.back.nbe1214finalhot6.domain.payment.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentCancelService;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentFacade;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.perf.ConcurrentRunner.Task;
import com.back.nbe1214finalhot6.support.perf.PerfFixture;
import com.back.nbe1214finalhot6.support.perf.PerfFixture.CompletedPayment;
import com.back.nbe1214finalhot6.support.perf.PerfReport;
import com.back.nbe1214finalhot6.support.perf.PerfReport.Environment;
import com.back.nbe1214finalhot6.support.perf.PerfRounds;
import com.back.nbe1214finalhot6.support.perf.PerfRounds.Round;
import com.back.nbe1214finalhot6.support.perf.RunResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 결제 동시성 기준값 S1~S4: 동시에 몰려도 돈이 정확한가, 그리고 얼마나 걸리나.
 *
 * <p>각 시나리오는 그 상황을 막는 보호 장치가 하나씩 있다. 기준값(현재 코드)에서는 불변 조건이 모두 지켜져야 하고, 실험 A(장치를 뺀 코드)에서는
 * 깨지는 것을 기록한다.
 *
 * <ul>
 *   <li>S1 같은 멱등키 동시 요청 → (지갑, 멱등키) 유니크 제약
 *   <li>S2 같은 QR 동시 결제 → QR 조건부 UPDATE
 *   <li>S3 같은 지갑 동시 결제 → 지갑 행 비관적 락
 *   <li>S4 같은 결제 동시 취소 → 결제 행 비관적 락 + 상태 확인
 * </ul>
 *
 * <p>커넥션 풀은 운영 기본값과 같은 10 으로 고정한다(테스트 프로필 기본 2 로 재면 락이 아니라 풀 대기를 재게 된다).
 */
@Tag("perf")
@IntegrationTest
@Import(PerfFixture.class)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=10")
class PaymentConcurrencyPerfTest {

  private static final long AMOUNT = 10_000L;
  private static final Duration TIMEOUT = Duration.ofSeconds(120);

  @Autowired PerfFixture fixture;
  @Autowired PaymentFacade paymentFacade;
  @Autowired PaymentCancelService cancelService;
  @Autowired DataSource dataSource;

  Environment env;

  @BeforeEach
  void setUp() {
    fixture.ensureBudget();
    env = PerfReport.environment(dataSource);
  }

  @Test
  @DisplayName("S1 같은 멱등키 동시 요청: 결제 행 1개, 차감 1번")
  void S1_같은_멱등키() {
    runAll(
        "S1-same-key",
        n -> {
          // 중복 차감이 일어나도 잔액 부족으로 가려지지 않게 N번 낼 만큼 넣어 둔다
          Long walletId = fixture.wallet(AMOUNT * n);
          String token = fixture.qr(fixture.merchant(), AMOUNT);
          String key = PerfFixture.newKey();
          PaymentRequest request = fixture.payRequest(walletId, token);
          List<Task> tasks = new ArrayList<>();
          for (int i = 0; i < n; i++) {
            tasks.add(Task.of("pay", () -> paymentFacade.pay(key, request).status().name()));
          }
          return round(
              tasks,
              r -> {
                long rows = fixture.paymentRows(walletId, key);
                long debits = fixture.ledgerLines(walletId, WalletTransactionType.PAYMENT_DEVMONEY);
                long balance = fixture.balance(walletId);
                return checks(
                    rows == 1 && debits == 1 && balance == AMOUNT * n - AMOUNT,
                    "paymentRows", rows, "debitLines", debits, "balance", balance,
                    "expectedBalance", AMOUNT * n - AMOUNT);
              });
        });
  }

  @Test
  @DisplayName("S2 같은 QR 동시 결제: 서로 다른 지갑 N개 중 1건만 완료")
  void S2_같은_QR() {
    runAll(
        "S2-same-qr",
        n -> {
          String token = fixture.qr(fixture.merchant(), AMOUNT);
          List<Long> wallets = new ArrayList<>();
          List<Task> tasks = new ArrayList<>();
          for (int i = 0; i < n; i++) {
            Long walletId = fixture.wallet(AMOUNT);
            wallets.add(walletId);
            PaymentRequest request = fixture.payRequest(walletId, token);
            String key = PerfFixture.newKey();
            tasks.add(Task.of("pay", () -> paymentFacade.pay(key, request).status().name()));
          }
          return round(
              tasks,
              r -> {
                long completed = fixture.completedPaymentsByQr(token);
                long debits =
                    wallets.stream()
                        .mapToLong(w -> fixture.ledgerLines(w, WalletTransactionType.PAYMENT_DEVMONEY))
                        .sum();
                String qrStatus = fixture.qrStatus(token);
                return checks(
                    completed == 1 && debits == 1 && "USED".equals(qrStatus),
                    "completedByQr", completed, "debitLinesAllWallets", debits, "qrStatus", qrStatus);
              });
        });
  }

  @Test
  @DisplayName("S3 같은 지갑 동시 결제: 잔액만큼만 완료되고 잔액은 음수가 되지 않는다")
  void S3_같은_지갑() {
    runAll(
        "S3-same-wallet",
        n -> {
          int affordable = Math.max(1, n / 2); // 절반만 낼 수 있는 잔액
          Long walletId = fixture.wallet(AMOUNT * affordable);
          Long merchantId = fixture.merchant();
          List<Task> tasks = new ArrayList<>();
          for (int i = 0; i < n; i++) {
            PaymentRequest request = fixture.payRequest(walletId, fixture.qr(merchantId, AMOUNT));
            String key = PerfFixture.newKey();
            tasks.add(Task.of("pay", () -> paymentFacade.pay(key, request).status().name()));
          }
          return round(
              tasks,
              r -> {
                long completed = fixture.payments(walletId, PaymentStatus.COMPLETED);
                long debits = fixture.ledgerLines(walletId, WalletTransactionType.PAYMENT_DEVMONEY);
                long balance = fixture.balance(walletId);
                return checks(
                    completed == affordable && debits == affordable && balance == 0,
                    "completed", completed, "expectedCompleted", affordable,
                    "debitLines", debits, "balance", balance);
              });
        });
  }

  @Test
  @DisplayName("S4 같은 결제 동시 취소: 환급 1번, 적립 취소 1번")
  void S4_같은_결제_취소() {
    runAll(
        "S4-same-cancel",
        n -> {
          CompletedPayment p = fixture.completedPayment(AMOUNT);
          List<Task> tasks = new ArrayList<>();
          for (int i = 0; i < n; i++) {
            tasks.add(
                Task.of(
                    "cancel",
                    () -> cancelService.cancel(p.merchantId(), p.paymentId()).status().name()));
          }
          return round(
              tasks,
              r -> {
                long refunds =
                    fixture.ledgerLines(p.walletId(), WalletTransactionType.PAYMENT_CANCEL_DEVMONEY);
                long balance = fixture.balance(p.walletId());
                long canceled = fixture.payments(p.walletId(), PaymentStatus.CANCELED);
                String earning = fixture.earningStatus(p.paymentId());
                return checks(
                    refunds == 1 && balance == AMOUNT && canceled == 1 && "CANCELED".equals(earning),
                    "refundLines", refunds, "balance", balance, "expectedBalance", AMOUNT,
                    "canceledPayments", canceled, "earningStatus", earning);
              });
        });
  }

  // ---------- 공통 ----------

  /** 스레드 수마다 예열 1회 + N라운드를 돌리고, 불변 조건이 하나라도 깨지면 실패시킨다(결과는 먼저 저장된다). */
  private void runAll(String base, IntFunction<Round> prepare) {
    String scenario = PerfRounds.scenarioName(base);
    List<String> violations = new ArrayList<>();
    for (int n : PerfRounds.threads()) {
      Map<String, Object> conditions = new LinkedHashMap<>();
      conditions.put("threads", n);
      conditions.put("amount", AMOUNT);
      conditions.put("variant", PerfRounds.variant());
      violations.addAll(
          PerfRounds.measure(
              scenario, "t" + n, conditions, PerfRounds.rounds(), true, TIMEOUT, env,
              () -> prepare.apply(n)));
    }
    assertThat(violations).as("불변 조건 위반").isEmpty();
  }

  private static Round round(List<Task> tasks, Function<RunResult, Map<String, Object>> check) {
    return Round.of(tasks, check);
  }

  private static Map<String, Object> checks(boolean ok, Object... pairs) {
    return PerfRounds.checks(ok, pairs);
  }
}
