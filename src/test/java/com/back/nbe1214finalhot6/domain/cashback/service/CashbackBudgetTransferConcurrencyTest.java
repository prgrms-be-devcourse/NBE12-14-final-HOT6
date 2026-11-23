package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetTransferRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackBudgetTransferRepository;
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
 * 월 예산 이월이 예산 확보와 동시에 들어와도 남은 예산이 음수가 되지 않고 금액이 어긋나지 않는지 확인한다.
 *
 * <p>실제 커밋이 오가야 하므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class CashbackBudgetTransferConcurrencyTest {

  private static final long UNIT = 100_000L;

  @Autowired private CashbackBudgetTransferService transferService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackBudgetTransferRepository transferRepository;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
    budgetService.putMonthly(2026, 12, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  @AfterEach
  void cleanUp() {
    transferRepository.deleteAll();
    monthlyBudgetRepository.deleteAll();
    annualBudgetRepository.deleteAll();
  }

  // 목적: 10건 분량의 11월 예산에 확보 15건과 이월 15건이 동시에 들어와도 성공한 확보와 이월의 합이 정확히 10건이고 남은 예산이 음수가 되지 않으며,
  // 12월이 받은 금액이 11월이 넘긴 금액과 같고 이력도 같은 건수인지 확인한다. 거절은 확보는 모자람, 이월은 CSH-011 이어야 한다.
  @Test
  @DisplayName("확보와 이월이 동시에 와도 남은 예산을 넘지 않고 금액이 일치한다")
  void concurrentReserveAndTransfer_neverOverspends() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(30);
    CountDownLatch ready = new CountDownLatch(30);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<String>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < 15; i++) {
        futures.add(executor.submit(task(ready, go, () -> budgetService.reserveBudget(2026, 11, UNIT).isReserved() ? "RESERVED" : "REJECTED")));
        futures.add(
            executor.submit(
                task(
                    ready,
                    go,
                    () -> {
                      try {
                        transferService.transfer(new BudgetTransferRequest(2026, 11, UNIT));
                        return "TRANSFERRED";
                      } catch (BusinessException e) {
                        assertThat(e.getErrorCode()).isEqualTo(CashbackErrorCode.TRANSFER_EXCEEDS_REMAINING);
                        return "REJECTED";
                      }
                    })));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      long reserved = 0;
      long transferred = 0;
      for (Future<String> future : futures) {
        String result = future.get(30, TimeUnit.SECONDS);
        if (result.equals("RESERVED")) {
          reserved++;
        } else if (result.equals("TRANSFERRED")) {
          transferred++;
        }
      }
      assertThat(reserved + transferred).isEqualTo(10);

      MonthlyBudgetResponse november = budgetService.getMonthly(2026, 11);
      assertThat(november.reservedAmount()).isEqualTo(reserved * UNIT);
      assertThat(november.carriedOutAmount()).isEqualTo(transferred * UNIT);
      assertThat(november.remainingAmount()).isZero();
      assertThat(budgetService.getMonthly(2026, 12).carriedInAmount()).isEqualTo(transferred * UNIT);
      assertThat(transferRepository.count()).isEqualTo(transferred);
    } finally {
      executor.shutdownNow();
    }
  }

  private Callable<String> task(CountDownLatch ready, CountDownLatch go, Callable<String> body) {
    return () -> {
      ready.countDown();
      go.await();
      return body.call();
    };
  }
}
