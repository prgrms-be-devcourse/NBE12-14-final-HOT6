package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
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
 * 운영자 중단 요청이 동시에 몰려도 전이와 이력이 한 번만 남는지 확인한다.
 *
 * <p>실제 커밋이 오가야 하므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class CashbackBudgetManualStopConcurrencyTest {

  private static final int YEAR = 2026;
  private static final int MONTH = 11;

  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackBudgetStateService stateService;
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

  private List<String> runConcurrently(int count, Runnable action) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(count);
    CountDownLatch ready = new CountDownLatch(count);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<String>> futures = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        Callable<String> task =
            () -> {
              ready.countDown();
              go.await();
              try {
                action.run();
                return "OK";
              } catch (BusinessException e) {
                assertThat(e.getErrorCode()).isEqualTo(CashbackErrorCode.BUDGET_ALREADY_STOPPED);
                return "REJECTED";
              }
            };
        futures.add(executor.submit(task));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      List<String> results = new ArrayList<>();
      for (Future<String> future : futures) {
        results.add(future.get(30, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  // 목적: 같은 달에 긴급 중단 10건이 동시에 와도 정확히 한 건만 성공하고 나머지는 CSH-012 로 거절되며 이력이 한 건뿐인지 확인한다.
  @Test
  @DisplayName("긴급 중단이 동시에 와도 한 번만 성공하고 이력은 한 건이다")
  void concurrentEmergencyStop() throws Exception {
    List<String> results = runConcurrently(10, () -> stateService.emergencyStopManually(YEAR, MONTH));

    assertThat(results.stream().filter("OK"::equals).count()).isEqualTo(1);
    assertThat(results.stream().filter("REJECTED"::equals).count()).isEqualTo(9);
    assertThat(monthlyBudgetRepository.findStatus(YEAR, MONTH)).contains(BudgetStatus.STOPPED);
    assertThat(stateLogRepository.count()).isEqualTo(1);
    assertThat(noticeRepository.count()).isEqualTo(1); // 공지도 전이와 같은 수만큼
  }

  // 목적: 공지 후 중단이 동시에 와도 모두 성공 응답(이미 공지면 그대로)이고 이력은 한 건뿐인지 확인한다.
  @Test
  @DisplayName("공지 후 중단이 동시에 와도 모두 성공하고 이력은 한 건이다")
  void concurrentNoticeStop() throws Exception {
    List<String> results = runConcurrently(10, () -> stateService.noticeStopManually(YEAR, MONTH));

    assertThat(results).containsOnly("OK");
    assertThat(monthlyBudgetRepository.findStatus(YEAR, MONTH)).contains(BudgetStatus.NOTICED);
    assertThat(stateLogRepository.count()).isEqualTo(1);
    assertThat(noticeRepository.count()).isEqualTo(1); // 공지도 전이와 같은 수만큼
  }
}
