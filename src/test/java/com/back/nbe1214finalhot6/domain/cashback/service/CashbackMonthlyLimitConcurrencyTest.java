package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyLimitRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 같은 이용자의 같은 달 한도를 동시에 사용해도 한도를 넘지 않는지 확인한다.
 *
 * <p>실제 커밋이 오가야 하므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class CashbackMonthlyLimitConcurrencyTest {

  private static final YearMonth MONTH = YearMonth.of(2026, 11);
  private static final long LIMIT = 50_000L;
  private static final long UNIT = 10_000L;

  @Autowired private CashbackMonthlyLimitService limitService;
  @Autowired private CashbackMonthlyLimitRepository limitRepository;

  @AfterEach
  void cleanUp() {
    limitRepository.deleteAll();
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

  // 목적: 행이 아직 없는 상태에서 20건이 동시에 사용을 시도해도(행을 동시에 처음 만드는 경쟁 포함) 오류 없이 정확히 5건만 성공하고
  // 사용량이 한도와 같은지 확인한다. 이용자의 월 적립 한도를 넘는 적립 0원을 보장한다.
  @Test
  @DisplayName("동시에 사용해도 한도를 넘지 않고 정확히 가능한 건수만 성공한다")
  void concurrentConsume_neverExceedsLimit() throws Exception {
    List<Callable<Boolean>> tasks = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      tasks.add(() -> limitService.consume(1L, MONTH, UNIT, LIMIT));
    }

    List<Boolean> results = runConcurrently(tasks);

    assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(5);
    assertThat(limitService.usedAmount(1L, MONTH)).isEqualTo(LIMIT);
    assertThat(limitRepository.count()).isEqualTo(1);
  }

  // 목적: 사용과 복원이 섞여 동시에 들어와도 사용량이 음수가 되거나 한도를 넘지 않는지 확인한다. 복원이 먼저 몰려 사용량이 모자라면 그 복원은
  // 정상적으로 거부되므로 결과로 받는다.
  @Test
  @DisplayName("사용과 복원이 섞여도 사용량이 0 이상 한도 이하로 유지된다")
  void concurrentConsumeAndRelease_staysInRange() throws Exception {
    limitService.consume(1L, MONTH, 30_000L, LIMIT);
    List<Callable<Boolean>> tasks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      tasks.add(() -> limitService.consume(1L, MONTH, UNIT, LIMIT));
      tasks.add(
          () -> {
            try {
              limitService.release(1L, MONTH, UNIT);
              return true;
            } catch (BusinessException e) {
              // 복원이 먼저 몰려 사용량이 모자라면 정상적으로 거부된다
              assertThat(e.getErrorCode()).isEqualTo(CashbackErrorCode.INVALID_BUDGET);
              return false;
            }
          });
    }

    runConcurrently(tasks);

    assertThat(limitService.usedAmount(1L, MONTH)).isBetween(0L, LIMIT);
  }
}
