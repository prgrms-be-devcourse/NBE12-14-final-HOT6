package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 예산 확보가 동시에 들어와도 예산을 넘지 않는지, 월 배정 수정과 섞여도 합계가 맞는지 확인한다.
 *
 * <p>실제 커밋이 오가야 하므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class CashbackBudgetReservationConcurrencyTest {

  private static final int YEAR = 2026;
  private static final int MONTH = 12;
  private static final long UNIT = 100_000L;

  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  // 연 예산 12,000,000 / 12월 배정 1,000,000
  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(YEAR, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(
        YEAR, MONTH, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  @AfterEach
  void cleanUp() {
    monthlyBudgetRepository.deleteAll();
    annualBudgetRepository.deleteAll();
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

  // 목적: 10건 분량의 월 예산에 30건이 동시에 확보를 요청해도 정확히 10건만 성공하고, 연·월 확보 합계가 배정과 같은지 확인한다.
  // 기획서 7절의 "동시 결제에서도 예산 초과 0원" 이다.
  @Test
  @DisplayName("동시에 확보해도 예산을 넘지 않고 정확히 남은 건수만 성공한다")
  void concurrentReserve_neverExceedsBudget() throws Exception {
    List<Callable<BudgetReserveResult>> tasks = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      tasks.add(() -> budgetService.reserveBudget(YEAR, MONTH, UNIT));
    }

    List<BudgetReserveResult> results = runConcurrently(tasks);

    assertThat(results.stream().filter(BudgetReserveResult::isReserved).count()).isEqualTo(10);
    assertThat(results.stream().filter(r -> r == BudgetReserveResult.INSUFFICIENT).count())
        .isEqualTo(20);
    assertThat(budgetService.getMonthly(YEAR, MONTH).reservedAmount()).isEqualTo(1_000_000L);
    assertThat(budgetService.getAnnual(YEAR).reservedAmount()).isEqualTo(1_000_000L);
  }

  // 목적: 확보와 복원이 섞여 동시에 들어와도 연·월 확보 금액이 서로 같고 음수가 되지 않는지 확인한다.
  @Test
  @DisplayName("확보와 복원이 섞여도 연·월 확보 금액이 일치한다")
  void concurrentReserveAndRelease_staysConsistent() throws Exception {
    budgetService.reserveBudget(YEAR, MONTH, 500_000L);
    List<Callable<Boolean>> tasks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      tasks.add(() -> budgetService.reserveBudget(YEAR, MONTH, UNIT).isReserved());
      tasks.add(
          () -> {
            budgetService.releaseBudget(YEAR, MONTH, UNIT);
            return true;
          });
    }

    runConcurrently(tasks);

    MonthlyBudgetResponse monthly = budgetService.getMonthly(YEAR, MONTH);
    AnnualBudgetResponse annual = budgetService.getAnnual(YEAR);
    assertThat(monthly.reservedAmount()).isEqualTo(annual.reservedAmount());
    assertThat(monthly.reservedAmount()).isBetween(0L, monthly.allocatedAmount());
  }

  // 목적: 월 배정 수정과 확보가 동시에 들어와도(같은 연 → 월 순서로 잠금) 교착 없이 끝나고, 수정이 성공했다면 확보가 새 배정을 넘지 않는지 확인한다.
  // 수정은 확보가 시작된 달에는 CSH-008 로 거부된다.
  @Test
  @DisplayName("월 배정 수정과 확보가 섞여도 교착 없이 배정을 넘지 않는다")
  void concurrentReserveAndAllocationChange_respectsAllocation() throws Exception {
    List<Callable<Object>> tasks = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      tasks.add(() -> budgetService.reserveBudget(YEAR, MONTH, UNIT));
    }
    tasks.add(
        () -> {
          try {
            return budgetService.putMonthly(
                YEAR, MONTH, new MonthlyBudgetRequest(500_000L, null)).allocatedAmount();
          } catch (BusinessException e) {
            // 확보가 먼저 시작된 달이라 수정이 거부됨
            assertThat(e.getErrorCode()).isEqualTo(CashbackErrorCode.BUDGET_IN_USE);
            return e.getErrorCode();
          }
        });

    List<Object> results = runConcurrently(tasks);

    MonthlyBudgetResponse monthly = budgetService.getMonthly(YEAR, MONTH);
    long reserved = results.stream().filter(BudgetReserveResult.RESERVED::equals).count() * UNIT;
    assertThat(monthly.reservedAmount()).isEqualTo(reserved);
    assertThat(monthly.reservedAmount())
        .isLessThanOrEqualTo(monthly.allocatedAmount() + monthly.carriedInAmount());
    assertThat(budgetService.getAnnual(YEAR).reservedAmount()).isEqualTo(reserved);
  }

  // 목적: 월 예산 쪽 복원이 실패하면(확보한 금액보다 많이 복원) 이미 처리한 연 예산 복원까지 롤백되는지 실제 커밋·롤백으로 확인한다.
  @Test
  @DisplayName("월 예산 복원이 실패하면 연 예산 복원도 롤백된다")
  void release_failureRollsBackAnnual() {
    budgetService.reserveBudget(YEAR, MONTH, UNIT);
    // 월 예산의 확보 금액만 일부러 줄여 연·월이 어긋난 상태를 만든다
    jdbcTemplate.update(
        "update cashback_monthly_budget set reserved_amount = 50000"
            + " where budget_year = ? and budget_month = ?",
        YEAR,
        MONTH);

    assertThatThrownBy(() -> budgetService.releaseBudget(YEAR, MONTH, UNIT))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);

    assertThat(budgetService.getAnnual(YEAR).reservedAmount()).isEqualTo(UNIT);
  }
}
