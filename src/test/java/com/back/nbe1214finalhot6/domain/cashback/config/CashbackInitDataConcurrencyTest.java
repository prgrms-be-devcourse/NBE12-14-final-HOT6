package com.back.nbe1214finalhot6.domain.cashback.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 두 서버가 동시에 시작해 연 예산을 함께 만들려 해도 시작에 실패하지 않고 먼저 만든 값이 남는지 확인한다.
 *
 * <p>실제 커밋이 오가야 하므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class CashbackInitDataConcurrencyTest {

  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private Clock clock;

  @BeforeEach
  @AfterEach
  void cleanUp() {
    annualBudgetRepository.deleteAll();
  }

  private CashbackInitData initData(long amount) {
    CashbackProperties properties =
        new CashbackProperties(
            new BigDecimal("0.10"),
            new BigDecimal("0.5"),
            3,
            3,
            0,
            1,
            null,
            null,
            new CashbackProperties.Init(true, amount));
    return new CashbackInitData(properties, annualBudgetRepository, budgetService, clock);
  }

  // 목적: 한쪽이 만들고 아직 커밋하지 않은 사이 다른 쪽이 "없음"을 보고 만들려 하면, 유니크 제약에 걸려도 예외 없이 끝나고 먼저 만든 값이 남는지 확인한다.
  @Test
  @DisplayName("다른 쪽이 먼저 만드는 중이어도 시작에 실패하지 않고 그 값이 남는다")
  void losingSide_keepsFirstValue() throws Exception {
    int year = Year.now(clock).getValue();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch inserted = new CountDownLatch(1);
    CountDownLatch commit = new CountDownLatch(1);
    try {
      Future<?> first =
          executor.submit(
              () ->
                  transactionTemplate.executeWithoutResult(
                      status -> {
                        budgetService.createAnnualIfAbsent(year, 7_000_000L);
                        inserted.countDown();
                        await(commit);
                      }));
      assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();

      Future<?> second = executor.submit(() -> initData(100_000_000L).run(null));
      // 두 번째는 같은 연도를 넣다가 첫 번째의 커밋을 기다린다
      assertThatThrownBy(() -> second.get(1, TimeUnit.SECONDS))
          .isInstanceOf(TimeoutException.class);

      commit.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);

      assertThat(annualBudgetRepository.count()).isEqualTo(1);
      assertThat(annualBudgetRepository.findByBudgetYear(year))
          .get()
          .satisfies(budget -> assertThat(budget.getTotalAmount()).isEqualTo(7_000_000L));
    } finally {
      commit.countDown();
      executor.shutdownNow();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("커밋 신호를 기다리다 시간이 지났습니다");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
