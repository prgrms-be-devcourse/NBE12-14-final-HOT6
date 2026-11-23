package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackNoticeRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyLimitRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletTransactionRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 지급 배치. 입금과 상태 전환과 예산 이동이 한 건 단위로 함께 일어나는지, 같은 건을 두 번 지급하지 않는지 확인한다. 테스트 시계는 2026-11-18 09:00 KST 이고
 * 지급 예정 시각은 2026-11-22 00:00 이다.
 *
 * <p>건별 실패와 동시 실행을 확인하려면 실제 커밋이 오가야 하므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class CashbackPayoutServiceTest {

  private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 18, 9, 0);
  private static final AtomicLong USER_SEQ = new AtomicLong(System.currentTimeMillis());

  @Autowired private CashbackPayoutService payoutService;
  @Autowired private CashbackService cashbackService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackEarningRepository earningRepository;
  @Autowired private CashbackMonthlyLimitRepository limitRepository;
  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  @Autowired private CashbackNoticeRepository noticeRepository;
  @Autowired private WalletRepository walletRepository;
  @Autowired private WalletTransactionRepository walletTransactionRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Clock clock;

  // 연 예산 12,000,000 / 11월 배정 1,000,000, 기본 요율 3%
  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  @AfterEach
  void cleanUp() {
    earningRepository.deleteAll();
    limitRepository.deleteAll();
    walletTransactionRepository.deleteAll();
    walletRepository.deleteAll();
    noticeRepository.deleteAll(); // 상태 전이·요율 인하가 남긴 공지가 다른 테스트에 섞이지 않게 한다
    monthlyBudgetRepository.deleteAll();
    annualBudgetRepository.deleteAll();
  }

  private Long saveWallet() {
    return walletRepository
        .save(Wallet.create(USER_SEQ.incrementAndGet(), LocalDateTime.now(clock)))
        .getId();
  }

  /** 12,000원 사용분이면 360원이 적립 예정으로 확보된다. */
  private CashbackEarning reserve(long paymentId, Long walletId) {
    cashbackService.reserve(new CashbackReserveRequest(paymentId, walletId, true, 12_000L, AT));
    return earningRepository.findByPaymentId(paymentId).orElseThrow();
  }

  private void passPayableTime() {
    ((MutableClock) clock).advance(Duration.ofDays(5));
  }

  private long cashbackBalance(Long walletId) {
    return walletRepository.findById(walletId).orElseThrow().getCashbackBalance();
  }

  private EarningStatus status(Long earningId) {
    return earningRepository.findById(earningId).orElseThrow().getStatus();
  }

  // 목적: 지급일이 된 적립 예정 건이 지갑 캐시백 잔액에 입금되고 원장에 적립 건 ID 로 남고, 상태가 지급 완료가 되며 예산의 확보 금액이 지급
  // 금액으로 옮겨지는지(남은 예산은 그대로) 확인한다.
  @Test
  @DisplayName("지급일이 된 건을 입금하고 지급 완료로 바꾸며 예산을 확보에서 지급으로 옮긴다")
  void run_paysDueEarning() {
    Long walletId = saveWallet();
    CashbackEarning earning = reserve(1L, walletId);
    long remainingBefore = budgetService.getMonthly(2026, 11).remainingAmount();
    passPayableTime();

    PayoutRunResponse result = payoutService.run();

    assertThat(result).isEqualTo(new PayoutRunResponse(1, 0, 0));
    assertThat(cashbackBalance(walletId)).isEqualTo(360L);
    WalletTransaction ledger =
        walletTransactionRepository
            .findByTypeAndRefKey(WalletTransactionType.CASHBACK_EARN, earning.getId())
            .orElseThrow();
    assertThat(ledger.getWalletId()).isEqualTo(walletId);
    assertThat(ledger.getAmount()).isEqualTo(360L);
    CashbackEarning paid = earningRepository.findById(earning.getId()).orElseThrow();
    assertThat(paid.getStatus()).isEqualTo(EarningStatus.PAID);
    assertThat(paid.getPaidAt()).isNotNull();
    var monthly = budgetService.getMonthly(2026, 11);
    assertThat(monthly.reservedAmount()).isZero();
    assertThat(monthly.paidAmount()).isEqualTo(360L);
    assertThat(monthly.remainingAmount()).isEqualTo(remainingBefore);
    assertThat(budgetService.getAnnual(2026).reservedAmount()).isZero();
    assertThat(budgetService.getAnnual(2026).paidAmount()).isEqualTo(360L);
  }

  // 목적: 지급 예정 시각 전의 건, 제외 건, 취소된 건은 지급 대상이 아닌지 확인한다.
  @Test
  @DisplayName("지급일 전·제외·취소 건은 지급하지 않는다")
  void run_ignoresNotDueExcludedAndCanceled() {
    Long walletId = saveWallet();
    reserve(1L, walletId);
    cashbackService.reserve(new CashbackReserveRequest(2L, walletId, false, 12_000L, AT));
    reserve(3L, walletId);
    cashbackService.cancel(3L);

    assertThat(payoutService.run()).isEqualTo(new PayoutRunResponse(0, 0, 0));

    passPayableTime();
    assertThat(payoutService.run()).isEqualTo(new PayoutRunResponse(1, 0, 0));
    assertThat(cashbackBalance(walletId)).isEqualTo(360L);
  }

  // 목적: 배치를 다시 돌려도 이미 지급한 건을 지갑에 두 번 넣지 않는지 확인한다.
  @Test
  @DisplayName("배치를 다시 돌려도 한 번만 입금한다")
  void run_isIdempotent() {
    Long walletId = saveWallet();
    reserve(1L, walletId);
    passPayableTime();

    payoutService.run();
    PayoutRunResponse again = payoutService.run();

    assertThat(again).isEqualTo(new PayoutRunResponse(0, 0, 0));
    assertThat(cashbackBalance(walletId)).isEqualTo(360L);
    assertThat(walletTransactionRepository.count()).isEqualTo(1);
    assertThat(budgetService.getMonthly(2026, 11).paidAmount()).isEqualTo(360L);
  }

  // 목적: 탈퇴한 지갑이나 없는 지갑의 건이 실패로 세어지고 적립 예정으로 남으며, 그 건이 다른 건의 지급을 막지 않는지 확인한다.
  @Test
  @DisplayName("지갑 오류가 난 건은 적립 예정으로 남고 나머지 건은 지급한다")
  void run_isolatesFailures() {
    Long withdrawn = saveWallet();
    Wallet wallet = walletRepository.findById(withdrawn).orElseThrow();
    CashbackEarning blocked = reserve(1L, withdrawn);
    CashbackEarning missing = reserve(2L, 987_654_321L);
    Long healthy = saveWallet();
    CashbackEarning ok = reserve(3L, healthy);
    wallet.withdraw(LocalDateTime.now(clock));
    walletRepository.save(wallet);
    passPayableTime();

    PayoutRunResponse result = payoutService.run();

    assertThat(result).isEqualTo(new PayoutRunResponse(1, 0, 2));
    assertThat(status(blocked.getId())).isEqualTo(EarningStatus.PENDING);
    assertThat(status(missing.getId())).isEqualTo(EarningStatus.PENDING);
    assertThat(status(ok.getId())).isEqualTo(EarningStatus.PAID);
    assertThat(cashbackBalance(healthy)).isEqualTo(360L);
    assertThat(cashbackBalance(withdrawn)).isZero();
  }

  // 목적: 예산 이동이 실패하면(확보한 금액이 모자람) 이미 반영한 지갑 입금과 상태 전환이 함께 롤백되어 적립 예정으로 남는지 확인한다.
  @Test
  @DisplayName("예산 이동이 실패하면 입금과 상태 전환도 함께 롤백된다")
  void run_rollsBackWholeItem() {
    Long walletId = saveWallet();
    CashbackEarning earning = reserve(1L, walletId);
    jdbcTemplate.update("update cashback_monthly_budget set reserved_amount = 0");
    passPayableTime();

    PayoutRunResponse result = payoutService.run();

    assertThat(result).isEqualTo(new PayoutRunResponse(0, 0, 1));
    assertThat(status(earning.getId())).isEqualTo(EarningStatus.PENDING);
    assertThat(cashbackBalance(walletId)).isZero();
    assertThat(walletTransactionRepository.count()).isZero();
  }

  // 목적: 한 번에 읽는 건수보다 많은 건도 모두 처리하고, 실패한 건이 앞에 있어도 같은 배치에서 반복해 잡히지 않고 끝나는지 확인한다.
  @Test
  @DisplayName("한 번에 읽는 건수보다 많아도 모두 처리하고 실패한 건에서 멈추지 않는다")
  void run_processesAllPages() {
    reserve(1L, 987_654_321L);
    Long walletId = saveWallet();
    for (long paymentId = 2; paymentId <= 6; paymentId++) {
      reserve(paymentId, walletId);
    }
    passPayableTime();

    PayoutRunResponse result = payoutService.run(2);

    assertThat(result).isEqualTo(new PayoutRunResponse(5, 0, 1));
    assertThat(cashbackBalance(walletId)).isEqualTo(5 * 360L);
  }

  // 목적: 지급 배치 두 개가 같은 건들을 동시에 처리해도 각 건이 한 번만 입금되고 예산도 한 번만 옮겨지는지 확인한다. 입금 총액이 건수 × 적립액과
  // 같고 원장 건수가 건수와 같아야 한다.
  @Test
  @DisplayName("지급 배치가 동시에 돌아도 각 건을 한 번만 지급한다")
  void run_concurrentRunsPayOnce() throws Exception {
    Long walletId = saveWallet();
    for (long paymentId = 1; paymentId <= 10; paymentId++) {
      reserve(paymentId, walletId);
    }
    passPayableTime();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<PayoutRunResponse>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        Callable<PayoutRunResponse> task =
            () -> {
              go.await();
              return payoutService.run();
            };
        futures.add(executor.submit(task));
      }
      go.countDown();
      int paid = 0;
      int failed = 0;
      for (Future<PayoutRunResponse> future : futures) {
        PayoutRunResponse result = future.get(30, TimeUnit.SECONDS);
        paid += result.paid();
        failed += result.failed();
      }

      assertThat(paid).isEqualTo(10);
      assertThat(failed).isZero();
    } finally {
      executor.shutdownNow();
    }
    assertThat(cashbackBalance(walletId)).isEqualTo(10 * 360L);
    assertThat(walletTransactionRepository.count()).isEqualTo(10);
    assertThat(budgetService.getMonthly(2026, 11).paidAmount()).isEqualTo(10 * 360L);
    assertThat(budgetService.getMonthly(2026, 11).reservedAmount()).isZero();
  }
}
