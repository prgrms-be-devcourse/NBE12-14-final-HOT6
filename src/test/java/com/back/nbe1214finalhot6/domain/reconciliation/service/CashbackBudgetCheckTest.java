package com.back.nbe1214finalhot6.domain.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationResult;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationStatus;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.ReconciliationResultRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

// 테스트 시계는 2026-11-18 09:00 KST 에서 시작하므로 대사 기준일은 11/17 이다. 예산은 12월분으로 만든다.
// 예산 대사는 모든 예산 행을 보므로, 다른 테스트가 남긴 행이 섞이지 않게 앞뒤로 지운다.
@IntegrationTest
class CashbackBudgetCheckTest {

  private static final int YEAR = 2026;
  private static final int MONTH = 12;
  private static final LocalDate DAY = LocalDate.of(2026, 11, 17);
  private static final LocalDate LIMIT_MONTH = LocalDate.of(2026, 12, 1);
  private static final long AMOUNT = 500L;

  @Autowired ReconciliationService reconciliationService;
  @Autowired ReconciliationResultRepository resultRepository;
  @Autowired CashbackBudgetService budgetService;
  @Autowired CashbackAnnualBudgetRepository annualRepository;
  @Autowired CashbackMonthlyBudgetRepository monthlyRepository;
  @Autowired CashbackEarningRepository earningRepository;
  @Autowired TransactionTemplate transactionTemplate;
  @Autowired MutableClock clock;

  private long paymentSeq;

  @BeforeEach
  void setUp() {
    cleanUp();
    budgetService.putAnnual(YEAR, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(
        YEAR, MONTH, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  @AfterEach
  void cleanUp() {
    resultRepository.deleteAll();
    earningRepository.deleteAll();
    monthlyRepository.deleteAll();
    annualRepository.deleteAll();
  }

  @Test
  @DisplayName("확보한 만큼 지급 대기 적립 건이 있으면 예산 대사가 일치한다")
  void 확보_일치() {
    pendingEarning();
    budgetService.reserveBudget(YEAR, MONTH, AMOUNT);

    ReconciliationResult result = run();

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
    assertThat(result.getCheckedCount()).isEqualTo(4);
  }

  @Test
  @DisplayName("지급이 끝난 적립 건과 예산의 지급 금액이 같으면 일치한다")
  void 지급_일치() {
    Long earningId = pendingEarning();
    budgetService.reserveBudget(YEAR, MONTH, AMOUNT);
    budgetService.settleBudget(YEAR, MONTH, AMOUNT);
    markPaid(earningId);

    assertThat(run().getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
  }

  @Test
  @DisplayName("지급 뒤에 회수해도 예산은 되돌리지 않으므로 일치한다")
  void 회수_일치() {
    Long earningId = pendingEarning();
    budgetService.reserveBudget(YEAR, MONTH, AMOUNT);
    budgetService.settleBudget(YEAR, MONTH, AMOUNT);
    markPaid(earningId);
    transactionTemplate.executeWithoutResult(
        status ->
            earningRepository.clawBackIfPaid(
                earningId,
                LocalDateTime.now(clock),
                EarningStatus.PAID,
                EarningStatus.CLAWED_BACK));

    assertThat(run().getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
  }

  @Test
  @DisplayName("지급 전에 취소하고 예산을 되돌렸으면 일치한다")
  void 지급전취소_일치() {
    Long earningId = pendingEarning();
    budgetService.reserveBudget(YEAR, MONTH, AMOUNT);
    transactionTemplate.executeWithoutResult(
        status ->
            earningRepository.cancelIfPending(
                earningId,
                LocalDateTime.now(clock),
                EarningStatus.PENDING,
                EarningStatus.CANCELED));
    budgetService.releaseBudget(YEAR, MONTH, AMOUNT);

    assertThat(run().getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
  }

  @Test
  @DisplayName("적립 건 없이 예산만 확보했으면 확보 금액 불일치로 잡힌다")
  void 확보_불일치() {
    budgetService.reserveBudget(YEAR, MONTH, AMOUNT);

    ReconciliationResult result = run();

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
    assertThat(result.getMismatchRefs()).isEqualTo("month=2026-12:RESERVED,year=2026:RESERVED");
  }

  @Test
  @DisplayName("예산은 지급으로 옮겼는데 적립 건이 지급 대기 그대로면 확보와 지급 금액이 모두 불일치로 잡힌다")
  void 지급_불일치() {
    pendingEarning();
    budgetService.reserveBudget(YEAR, MONTH, AMOUNT);
    budgetService.settleBudget(YEAR, MONTH, AMOUNT);

    ReconciliationResult result = run();

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
    assertThat(result.getMismatchRefs())
        .isEqualTo(
            "month=2026-12:RESERVED,month=2026-12:PAID,year=2026:RESERVED,year=2026:PAID");
  }

  @Test
  @DisplayName("예산을 확보하지 않았는데 지급 대기 적립 건이 있으면 불일치로 잡힌다")
  void 적립건만_있음_불일치() {
    pendingEarning();

    ReconciliationResult result = run();

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
    assertThat(result.getMismatchRefs()).isEqualTo("month=2026-12:RESERVED,year=2026:RESERVED");
  }

  private ReconciliationResult run() {
    return reconciliationService.run(DAY).stream()
        .filter(r -> r.getType() == ReconciliationType.CASHBACK_BUDGET)
        .findFirst()
        .orElseThrow();
  }

  private Long pendingEarning() {
    LocalDateTime now = LocalDateTime.now(clock);
    return earningRepository
        .save(
            CashbackEarning.pending(
                ++paymentSeq,
                1L,
                AMOUNT * 10,
                now,
                new BigDecimal("0.1").setScale(CashbackPolicy.RATE_SCALE),
                null,
                AMOUNT,
                now,
                LIMIT_MONTH,
                now))
        .getId();
  }

  private void markPaid(Long earningId) {
    transactionTemplate.executeWithoutResult(
        status ->
            earningRepository.markPaidIfPending(
                earningId, LocalDateTime.now(clock), EarningStatus.PENDING, EarningStatus.PAID));
  }
}
