package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetCloseRunResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetTransferType;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackBudgetTransfer;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackBudgetTransferRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
 * 월 마감 자동 이월. 11월·12월 배정 각 1,000,000. 테스트 시계는 2026-11-18 09:00 KST 다.
 *
 * <p>건별 트랜잭션과 동시 실행을 확인하려면 실제 커밋이 오가야 하므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class CashbackBudgetCloseServiceTest {

  private static final BigDecimal RATE = new BigDecimal("0.03");

  @Autowired private CashbackBudgetCloseService closeService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackBudgetTransferRepository transferRepository;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Clock clock;

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, RATE));
    budgetService.putMonthly(2026, 12, new MonthlyBudgetRequest(1_000_000L, RATE));
  }

  @AfterEach
  void cleanUp() {
    transferRepository.deleteAll();
    monthlyBudgetRepository.deleteAll();
    annualBudgetRepository.deleteAll();
  }

  private void advanceDays(long days) {
    ((MutableClock) clock).advance(Duration.ofDays(days));
  }

  private MonthlyBudgetResponse monthly(int year, int month) {
    return budgetService.getMonthly(year, month);
  }

  private BudgetStatus status(int year, int month) {
    return monthlyBudgetRepository.findStatus(year, month).orElseThrow();
  }

  private void setUpNextYear() {
    budgetService.putAnnual(2027, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2027, 1, new MonthlyBudgetRequest(1_000_000L, RATE));
  }

  // 목적: 달이 바뀐 뒤 지난달의 남은 예산(확보를 뺀 금액)이 전액 다음 달로 이월되고, 지난달은 마감되며 이력이 자동(AUTO, 처리자 없음)으로 남고 마감된 달은
  // 이후 확보를 거절하는지 확인한다.
  @Test
  @DisplayName("월이 바뀌면 지난달의 남은 예산이 다음 달로 이월되고 지난달은 마감된다")
  void run_closesAndTransfers() {
    budgetService.reserveBudget(2026, 11, 300_000L);
    advanceDays(13); // 2026-12-01

    BudgetCloseRunResponse result = closeService.run();

    assertThat(result).isEqualTo(new BudgetCloseRunResponse(1, 0, 0));
    assertThat(status(2026, 11)).isEqualTo(BudgetStatus.CLOSED);
    MonthlyBudgetResponse november = monthly(2026, 11);
    assertThat(november.carriedOutAmount()).isEqualTo(700_000L);
    assertThat(november.remainingAmount()).isZero();
    assertThat(monthly(2026, 12).carriedInAmount()).isEqualTo(700_000L);
    assertThat(monthly(2026, 12).remainingAmount()).isEqualTo(1_700_000L);
    List<CashbackBudgetTransfer> transfers = transferRepository.findAll();
    assertThat(transfers).hasSize(1);
    assertThat(transfers.get(0).getType()).isEqualTo(BudgetTransferType.AUTO);
    assertThat(transfers.get(0).getCreatedBy()).isNull();
    assertThat(transfers.get(0).getAmount()).isEqualTo(700_000L);
    assertThat(budgetService.reserveBudget(2026, 11, 1_000L)).isEqualTo(BudgetReserveResult.NOT_ACTIVE);
  }

  // 목적: 아직 월이 바뀌지 않았으면(이번 달 예산) 아무것도 마감하지 않고, 다시 돌려도 이미 마감한 달은 건드리지 않는지(멱등) 확인한다.
  @Test
  @DisplayName("이번 달은 마감하지 않고 다시 돌려도 한 번만 이월한다")
  void run_isIdempotent() {
    assertThat(closeService.run()).isEqualTo(new BudgetCloseRunResponse(0, 0, 0));
    advanceDays(13);

    closeService.run();
    BudgetCloseRunResponse again = closeService.run();

    assertThat(again).isEqualTo(new BudgetCloseRunResponse(0, 0, 0));
    assertThat(transferRepository.count()).isEqualTo(1);
    assertThat(monthly(2026, 12).carriedInAmount()).isEqualTo(1_000_000L);
  }

  // 목적: 남은 예산이 0 이면 이월 이력 없이 마감만 되는지 확인한다.
  @Test
  @DisplayName("남은 예산이 0 이면 이월 없이 마감만 한다")
  void run_zeroRemaining() {
    budgetService.reserveBudget(2026, 11, 1_000_000L);
    advanceDays(13);

    BudgetCloseRunResponse result = closeService.run();

    assertThat(result.closed()).isEqualTo(1);
    assertThat(status(2026, 11)).isEqualTo(BudgetStatus.CLOSED);
    assertThat(transferRepository.count()).isZero();
    assertThat(monthly(2026, 12).carriedInAmount()).isZero();
  }

  // 목적: 다음 달 월 예산이 없으면 마감하지 않고 보류하며(건너뜀), 다음 달 예산이 생긴 뒤 다음 배치에서 처리되는지 확인한다.
  @Test
  @DisplayName("다음 달 월 예산이 없으면 보류하고 생긴 뒤 처리한다")
  void run_holdsUntilNextBudgetExists() {
    advanceDays(44); // 2027-01-01: 11월·12월 모두 지난 달

    BudgetCloseRunResponse held = closeService.run();

    // 11월은 12월이 있어서 마감, 12월은 다음 해 연·월 예산이 없어서 보류
    assertThat(held).isEqualTo(new BudgetCloseRunResponse(1, 1, 0));
    assertThat(status(2026, 12)).isEqualTo(BudgetStatus.ACTIVE);

    setUpNextYear();
    BudgetCloseRunResponse resumed = closeService.run();

    assertThat(resumed).isEqualTo(new BudgetCloseRunResponse(1, 0, 0));
    assertThat(status(2026, 12)).isEqualTo(BudgetStatus.CLOSED);
  }

  // 목적: 12월 마감이 다음 해 1월로 이월되고 연 예산도 맞춰지며(넘기는 해 이월 유출, 받는 해 이월 유입), 앞 달(11월)에서 넘어온 금액이 12월에 포함된 채 함께
  // 이월되는지(오래된 달부터 처리) 확인한다.
  @Test
  @DisplayName("12월 마감은 다음 해 1월로 이월하고 밀린 앞 달의 이월분도 함께 넘어간다")
  void run_crossYearAndChain() {
    setUpNextYear();
    advanceDays(45); // 2027-01-02

    BudgetCloseRunResponse result = closeService.run();

    assertThat(result).isEqualTo(new BudgetCloseRunResponse(2, 0, 0));
    assertThat(status(2026, 11)).isEqualTo(BudgetStatus.CLOSED);
    assertThat(status(2026, 12)).isEqualTo(BudgetStatus.CLOSED);
    // 11월 1,000,000 -> 12월(2,000,000) -> 1월
    assertThat(monthly(2027, 1).carriedInAmount()).isEqualTo(2_000_000L);
    assertThat(monthly(2027, 1).remainingAmount()).isEqualTo(3_000_000L);
    assertThat(budgetService.getAnnual(2026).carriedOutAmount()).isEqualTo(2_000_000L);
    assertThat(budgetService.getAnnual(2027).carriedInAmount()).isEqualTo(2_000_000L);
    assertThat(transferRepository.count()).isEqualTo(2);
  }

  // 목적: 적립 중단 상태의 달도 남은 예산이 이월되고 마감되는지 확인한다.
  @Test
  @DisplayName("적립이 중단된 달도 남은 예산을 이월하고 마감한다")
  void run_stoppedMonth() {
    jdbcTemplate.update(
        "update cashback_monthly_budget set status = 'STOPPED' where budget_year = 2026 and budget_month = 11");
    advanceDays(13);

    closeService.run();

    assertThat(status(2026, 11)).isEqualTo(BudgetStatus.CLOSED);
    assertThat(monthly(2026, 12).carriedInAmount()).isEqualTo(1_000_000L);
  }

  // 목적: 마감 뒤 그 달의 적립 건이 취소돼 확보가 반환되면 그 금액이 마감된 달의 남은 예산으로 돌아오고 다음 달로 다시 이월되지 않는지 확인한다.
  @Test
  @DisplayName("마감 뒤 취소로 반환된 확보는 마감된 달에 남고 다시 이월하지 않는다")
  void run_cancelAfterCloseStaysInClosedMonth() {
    budgetService.reserveBudget(2026, 11, 100_000L);
    advanceDays(13);
    closeService.run();

    budgetService.releaseBudget(2026, 11, 100_000L);
    closeService.run();

    assertThat(monthly(2026, 11).remainingAmount()).isEqualTo(100_000L);
    assertThat(monthly(2026, 12).carriedInAmount()).isEqualTo(900_000L);
    assertThat(transferRepository.count()).isEqualTo(1);
  }

  // 목적: 마감 배치 두 개가 동시에 돌아도 각 달이 한 번만 마감·이월되고 이월 금액·이력이 어긋나지 않는지 확인한다.
  @Test
  @DisplayName("마감 배치가 동시에 돌아도 한 번만 이월한다")
  void run_concurrentRuns() throws Exception {
    advanceDays(13);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<BudgetCloseRunResponse>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        futures.add(
            executor.submit(
                () -> {
                  go.await();
                  return closeService.run();
                }));
      }
      go.countDown();
      int closed = 0;
      int failed = 0;
      for (Future<BudgetCloseRunResponse> future : futures) {
        BudgetCloseRunResponse result = future.get(30, TimeUnit.SECONDS);
        closed += result.closed();
        failed += result.failed();
      }
      assertThat(closed).isEqualTo(1);
      assertThat(failed).isZero();
    } finally {
      executor.shutdownNow();
    }

    assertThat(transferRepository.count()).isEqualTo(1);
    assertThat(monthly(2026, 11).carriedOutAmount()).isEqualTo(1_000_000L);
    assertThat(monthly(2026, 12).carriedInAmount()).isEqualTo(1_000_000L);
  }
}
