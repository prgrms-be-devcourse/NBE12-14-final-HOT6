package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackBudgetStateLog;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackBudgetStateLogRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackNoticeRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
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

/**
 * 결제가 동시에 몰려 공지선·긴급선을 넘어도 상태 전이와 이력이 단계마다 정확히 한 번만 남는지 확인한다.
 *
 * <p>실제 커밋이 오가야 하므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class CashbackBudgetStateConcurrencyTest {

  private static final int YEAR = 2026;
  private static final int MONTH = 11;
  private static final long UNIT = 100_000L;

  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  @Autowired private CashbackBudgetStateLogRepository stateLogRepository;
  @Autowired private CashbackNoticeRepository noticeRepository;

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(YEAR, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(
        YEAR, MONTH, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  @AfterEach
  void cleanUp() {
    stateLogRepository.deleteAll();
    noticeRepository.deleteAll(); // 상태 전이가 남긴 공지가 다른 테스트에 섞이지 않게 한다
    monthlyBudgetRepository.deleteAll();
    annualBudgetRepository.deleteAll();
  }

  // 목적: 10건 분량의 월 예산에 30건이 동시에 확보를 요청해도 공지(아홉 번째로 남은 예산 100,000)와 중단(열 번째로 남은 예산 0)이 각각 한 번만
  // 일어나고, 이력도 두 건뿐이며 나머지 20건은 중단으로 거절되는지 확인한다.
  @Test
  @DisplayName("동시 확보로 선을 넘어도 전이와 이력은 단계마다 한 번이다")
  void concurrentReserve_transitionsOnce() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(30);
    CountDownLatch ready = new CountDownLatch(30);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<BudgetReserveResult>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < 30; i++) {
        Callable<BudgetReserveResult> task =
            () -> {
              ready.countDown();
              go.await();
              return budgetService.reserveBudget(YEAR, MONTH, UNIT);
            };
        futures.add(executor.submit(task));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      long reserved = 0;
      long rejected = 0;
      for (Future<BudgetReserveResult> future : futures) {
        BudgetReserveResult result = future.get(30, TimeUnit.SECONDS);
        if (result.isReserved()) {
          reserved++;
        } else {
          rejected++;
          assertThat(result).isEqualTo(BudgetReserveResult.NOT_ACTIVE);
        }
      }
      assertThat(reserved).isEqualTo(10);
      assertThat(rejected).isEqualTo(20);
    } finally {
      executor.shutdownNow();
    }

    assertThat(monthlyBudgetRepository.findStatus(YEAR, MONTH)).contains(BudgetStatus.STOPPED);
    Long id = monthlyBudgetRepository.findId(YEAR, MONTH).orElseThrow();
    List<CashbackBudgetStateLog> logs = stateLogRepository.findAllByMonthlyBudgetIdOrderByIdAsc(id);
    assertThat(logs).hasSize(2);
    assertThat(logs.get(0).getToStatus()).isEqualTo(BudgetStatus.NOTICED);
    assertThat(logs.get(1).getToStatus()).isEqualTo(BudgetStatus.STOPPED);
  }
}
