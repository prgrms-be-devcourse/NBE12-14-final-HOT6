package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyLimitRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
  @Autowired private JdbcTemplate jdbcTemplate;
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

  // 목적: 취소와 지급 전환이 동시에 와도 한쪽만 성공하는지 확인한다. 지급이 이기면 취소는 거절되고 예산·한도가 그대로이며, 취소가 이기면 지급
  // 전환은 0건이라 모두 되돌아가 있어야 한다. 두 결과가 섞이면 안 된다.
  @Test
  @DisplayName("취소와 지급 전환이 동시에 와도 한쪽만 성공한다")
  void cancelRacingPayout_onlyOneWins() throws Exception {
    int paidWins = 0;
    int cancelWins = 0;
    for (long paymentId = 1; paymentId <= 20; paymentId++) {
      reserve(paymentId, 10_000L);
      long id = paymentId;
      List<Callable<String>> tasks =
          List.of(
              () -> {
                try {
                  service.cancel(id);
                  return "CANCEL";
                } catch (BusinessException e) {
                  assertThat(e.getErrorCode()).isEqualTo(CashbackErrorCode.EARNING_ALREADY_PAID);
                  return "REJECTED";
                }
              },
              () ->
                  jdbcTemplate.update(
                              "update cashback_earning set status = 'PAID'"
                                  + " where payment_id = ? and status = 'PENDING'",
                              id)
                          == 1
                      ? "PAID"
                      : "PAYOUT_LOST");

      List<String> results = runConcurrently(tasks);

      EarningStatus status = earningRepository.findByPaymentId(paymentId).orElseThrow().getStatus();
      if (results.contains("PAID")) {
        paidWins++;
        assertThat(results).contains("REJECTED");
        assertThat(status).isEqualTo(EarningStatus.PAID);
      } else {
        cancelWins++;
        assertThat(results).containsExactly("CANCEL", "PAYOUT_LOST");
        assertThat(status).isEqualTo(EarningStatus.CANCELED);
      }
      // 이긴 쪽에 맞게, 지금까지 지급된 건만 한도와 예산에 남아 있다
      assertThat(limitService.usedAmount(WALLET, NOV)).isEqualTo(300L * paidWins);
      assertThat(monthlyReserved()).isEqualTo(300L * paidWins);
    }
    assertThat(paidWins + cancelWins).isEqualTo(20);
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
