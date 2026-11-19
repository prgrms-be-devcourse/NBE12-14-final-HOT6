package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackAnnualBudget;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 연 예산 총액 변경이 행 잠금을 쓰는지 확인한다.
 *
 * <p>실제 커밋이 오가야 하므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class CashbackBudgetLockTest {

  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    annualBudgetRepository.deleteAll();
  }

  // 목적: 다른 트랜잭션이 행을 잠그고 있으면 총액 변경이 기다리고, 그 트랜잭션이 커밋한 예약 금액을
  // 덮어쓰지 않는지 확인한다. 잠금이 없으면 총액 변경이 낡은 예약 금액(0)을 다시 저장해 500 이 사라진다.
  @Test
  @DisplayName("총액 변경은 행 잠금이 풀릴 때까지 기다리고 그 사이 커밋된 예약 금액을 유지한다")
  void changeTotal_waitsForLockAndKeepsReserved() throws Exception {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(1_000_000L));
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      // 결제가 연 예산 행을 잡고 예약 금액을 올리는 상황
      Future<?> payment =
          executor.submit(
              () ->
                  tx.executeWithoutResult(
                      status -> {
                        annualBudgetRepository.findWithLockByBudgetYear(2026);
                        locked.countDown();
                        await(release);
                        jdbcTemplate.update(
                            "update cashback_annual_budget set reserved_amount = 500"
                                + " where budget_year = 2026");
                      }));
      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

      Future<AnnualBudgetResponse> change =
          executor.submit(
              () -> budgetService.putAnnual(2026, new AnnualBudgetRequest(2_000_000L)));
      assertThatThrownBy(() -> change.get(1, TimeUnit.SECONDS))
          .isInstanceOf(TimeoutException.class);

      release.countDown();
      payment.get(5, TimeUnit.SECONDS);
      assertThat(change.get(5, TimeUnit.SECONDS).totalAmount()).isEqualTo(2_000_000L);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }

    CashbackAnnualBudget stored = annualBudgetRepository.findByBudgetYear(2026).orElseThrow();
    assertThat(stored.getTotalAmount()).isEqualTo(2_000_000L);
    assertThat(stored.getReservedAmount()).isEqualTo(500L);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
