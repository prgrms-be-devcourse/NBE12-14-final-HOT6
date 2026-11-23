package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyLimitRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletTransactionRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 적립 취소가 동시에 들어오거나 지급과 겹치거나 롤백될 때 예산·한도가 어긋나지 않는지 확인한다.
 *
 * <p>실제 커밋이 오가야 하므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class CashbackCancelConcurrencyTest {

  private static final long WALLET = 7L;
  private static final YearMonth NOV = YearMonth.of(2026, 11);
  private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 18, 9, 0);

  @Autowired private CashbackService service;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackMonthlyLimitService limitService;
  @Autowired private CashbackEarningRepository earningRepository;
  @Autowired private CashbackMonthlyLimitRepository limitRepository;
  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  @Autowired private CashbackPayoutProcessor payoutProcessor;
  @Autowired private WalletRepository walletRepository;
  @Autowired private WalletTransactionRepository walletTransactionRepository;
  @Autowired private Clock clock;
  @Autowired private TransactionTemplate transactionTemplate;

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
    monthlyBudgetRepository.deleteAll();
    annualBudgetRepository.deleteAll();
  }

  private void reserve(long paymentId, long walletUsed) {
    service.reserve(new CashbackReserveRequest(paymentId, WALLET, true, walletUsed, AT));
  }

  private long monthlyReserved() {
    return budgetService.getMonthly(2026, 11).reservedAmount();
  }

  /** 모든 작업을 같은 순간에 시작해서 결과를 모은다. */
  private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
    CountDownLatch ready = new CountDownLatch(tasks.size());
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<T>> futures = new ArrayList<>();
      for (Callable<T> task : tasks) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return task.call();
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get(20, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  // 목적: 같은 결제를 10건이 동시에 취소해도 모두 예외 없이 끝나고 예산·한도는 정확히 한 번만 되돌아가는지 확인한다. 두 번 되돌리면 확보
  // 금액보다 많이 되돌리려는 예외가 나거나 다른 결제의 몫이 줄어든다.
  @Test
  @DisplayName("같은 결제를 동시에 취소해도 한 번만 되돌린다")
  void concurrentCancel_restoresOnce() throws Exception {
    reserve(1L, 12_000L);
    reserve(2L, 10_000L);
    List<Callable<Boolean>> tasks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      tasks.add(
          () -> {
            service.cancel(1L);
            return true;
          });
    }

    runConcurrently(tasks);

    assertThat(earningRepository.findByPaymentId(1L).orElseThrow().getStatus())
        .isEqualTo(EarningStatus.CANCELED);
    assertThat(limitService.usedAmount(WALLET, NOV)).isEqualTo(300L);
    assertThat(monthlyReserved()).isEqualTo(300L);
    assertThat(budgetService.getAnnual(2026).reservedAmount()).isEqualTo(300L);
  }

  // 목적: 취소와 지급 배치가 동시에 와도 한쪽만 이기고 돈이 어긋나지 않는지 확인한다. 취소가 먼저면 입금 없이 취소(예산 반환)로 끝나고, 지급이
  // 먼저면 입금 뒤 회수(예산은 지급으로 옮겨진 채)로 끝난다. 어느 쪽이든 지갑 순잔액은 0, 월 한도는 돌아와 있어야 한다.
  @Test
  @DisplayName("취소와 지급 배치가 동시에 와도 한쪽 결과로만 끝나고 순잔액은 0 이다")
  void cancelRacingPayout_consistent() throws Exception {
    Long walletId =
        walletRepository.save(Wallet.create(System.nanoTime(), LocalDateTime.now(clock))).getId();
    ((MutableClock) clock).advance(Duration.ofDays(5));
    int cancelFirst = 0;
    int payFirst = 0;
    for (long paymentId = 1; paymentId <= 20; paymentId++) {
      service.reserve(new CashbackReserveRequest(paymentId, walletId, true, 10_000L, AT));
      Long earningId = earningRepository.findByPaymentId(paymentId).orElseThrow().getId();
      long id = paymentId;
      List<Callable<String>> tasks =
          List.of(
              () -> {
                service.cancel(id);
                return "CANCEL";
              },
              () -> payoutProcessor.pay(earningId).name());

      runConcurrently(tasks);
      // 취소가 지급 전 상태만 보고 지나간 경우를 위해 한 번 더 호출해 최종 상태를 확정한다(두 번째 호출은 이미 처리된 건에 무변화)
      service.cancel(id);

      EarningStatus status = earningRepository.findById(earningId).orElseThrow().getStatus();
      assertThat(status).isIn(EarningStatus.CANCELED, EarningStatus.CLAWED_BACK);
      long ledgerRows =
          walletTransactionRepository.findAll().stream()
              .filter(tx -> earningId.equals(tx.getRefKey()))
              .count();
      if (status == EarningStatus.CANCELED) {
        cancelFirst++;
        assertThat(ledgerRows).isZero();
      } else {
        payFirst++;
        assertThat(ledgerRows).isEqualTo(2);
      }
      assertThat(walletRepository.findById(walletId).orElseThrow().getCashbackBalance()).isZero();
      assertThat(limitService.usedAmount(walletId, NOV)).isZero();
      assertThat(monthlyReserved()).isZero();
      assertThat(budgetService.getMonthly(2026, 11).paidAmount()).isEqualTo(300L * payFirst);
    }
    assertThat(cancelFirst + payFirst).isEqualTo(20);
  }

  // 목적: 취소를 호출한 트랜잭션이 롤백되면 적립 건 상태, 한도, 예산 복원이 모두 함께 롤백되는지 확인한다.
  @Test
  @DisplayName("취소 트랜잭션이 롤백되면 복원도 함께 롤백된다")
  void cancel_rollsBackTogether() {
    reserve(1L, 12_000L);

    transactionTemplate.executeWithoutResult(
        status -> {
          service.cancel(1L);
          status.setRollbackOnly();
        });

    assertThat(earningRepository.findByPaymentId(1L).orElseThrow().getStatus())
        .isEqualTo(EarningStatus.PENDING);
    assertThat(limitService.usedAmount(WALLET, NOV)).isEqualTo(360L);
    assertThat(monthlyReserved()).isEqualTo(360L);
    assertThat(budgetService.getAnnual(2026).reservedAmount()).isEqualTo(360L);
  }
}
