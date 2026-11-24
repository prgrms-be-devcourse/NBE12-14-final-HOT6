package com.back.nbe1214finalhot6.domain.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationResult;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationStatus;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.ReconciliationResultRepository;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.service.DailySettlementService;
import com.back.nbe1214finalhot6.domain.settlement.service.MonthlySettlementService;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.MerchantWalletService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 테스트 시계는 2026-11-18 09:00 KST 에서 시작하므로 대사 기준일은 11/17 이다. 월 지급 대사는 그 전월인 10월을 본다.
@IntegrationTest
class ReconciliationCheckTest {

  private static final Long MERCHANT = 1L;
  private static final LocalDate D16 = LocalDate.of(2026, 11, 16);
  private static final LocalDate D17 = LocalDate.of(2026, 11, 17);
  private static final LocalDate OCTOBER_FIRST = LocalDate.of(2026, 10, 1);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 0, 30);
  private static final List<ReconciliationType> SETTLEMENT_TYPES =
      List.of(
          ReconciliationType.DAILY_NET_SALES,
          ReconciliationType.SETTLEMENT_CARRY,
          ReconciliationType.MONTHLY_PAYOUT);

  @Autowired ReconciliationService reconciliationService;
  @Autowired ReconciliationResultRepository resultRepository;
  @Autowired DailySettlementService dailyService;
  @Autowired MonthlySettlementService monthlyService;
  @Autowired DailySettlementRepository dailyRepository;
  @Autowired MonthlySettlementRepository monthlyRepository;
  @Autowired PaymentRepository paymentRepository;
  @Autowired MerchantWalletService walletService;
  @Autowired MerchantWalletRepository walletRepository;
  @Autowired MerchantAccountRepository accountRepository;
  @Autowired MerchantWalletTransactionRepository transactionRepository;

  @AfterEach
  void cleanUp() {
    resultRepository.deleteAll();
    transactionRepository.deleteAll();
    monthlyRepository.deleteAll();
    dailyRepository.deleteAll();
    paymentRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
  }

  @Test
  @DisplayName("정산이 결제와 맞으면 모든 종류가 일치로 저장된다")
  void 정상_모두일치() {
    saveCompleted(MERCHANT, 10_000L, D17.atTime(10, 0));
    dailyService.settle(D17);

    List<ReconciliationResult> results = reconciliationService.run(D17);

    assertThat(results).hasSize(ReconciliationType.values().length);
    assertThat(results)
        .filteredOn(r -> SETTLEMENT_TYPES.contains(r.getType()))
        .allMatch(r -> r.getStatus() == ReconciliationStatus.MATCHED);
    assertThat(resultRepository.count()).isEqualTo(ReconciliationType.values().length);
  }

  @Test
  @DisplayName("정산 이후 늦게 완료된 결제는 순매출 대사에서 불일치로 잡힌다")
  void 늦은결제_불일치() {
    saveCompleted(MERCHANT, 10_000L, D17.atTime(10, 0));
    dailyService.settle(D17);
    saveCompleted(MERCHANT, 5_000L, D17.atTime(23, 50));

    ReconciliationResult result = resultOf(ReconciliationType.DAILY_NET_SALES);

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
    assertThat(result.getMismatchRefs()).isEqualTo("merchantId=1");
  }

  @Test
  @DisplayName("결제가 있는데 정산 장부가 없으면 순매출 대사에서 불일치로 잡힌다")
  void 장부없음_불일치() {
    saveCompleted(MERCHANT, 10_000L, D17.atTime(10, 0));

    ReconciliationResult result = resultOf(ReconciliationType.DAILY_NET_SALES);

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
  }

  @Test
  @DisplayName("전일 이월과 어제의 다음 날 이월이 다르면 이월 대사에서 불일치로 잡힌다")
  void 이월_불연속() {
    dailyRepository.save(DailySettlement.create(MERCHANT, D16, 0L, 30_000L, 0L, NOW));
    dailyRepository.save(DailySettlement.create(MERCHANT, D17, 10_000L, 0L, 0L, NOW));

    ReconciliationResult result = resultOf(ReconciliationType.SETTLEMENT_CARRY);

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
    assertThat(result.getMismatchRefs()).isEqualTo("merchantId=1");
  }

  @Test
  @DisplayName("이월 부족분이 있는 가맹점의 오늘 장부가 없으면 이월 대사에서 불일치로 잡힌다")
  void 이월가맹점_장부누락() {
    dailyRepository.save(DailySettlement.create(MERCHANT, D16, 0L, 30_000L, 0L, NOW));

    ReconciliationResult result = resultOf(ReconciliationType.SETTLEMENT_CARRY);

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
    assertThat(result.getCheckedCount()).isEqualTo(1);
    assertThat(result.getMismatchCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("월 지급액과 일 정산 합, 지갑 원장이 모두 맞으면 월 지급 대사가 일치한다")
  void 월지급_일치() {
    walletService.createFor(MERCHANT);
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 5), 15_000L);
    monthlyService.settle(YearMonth.of(2026, 10));

    ReconciliationResult result = resultOf(ReconciliationType.MONTHLY_PAYOUT);

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
    assertThat(result.getCheckedCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("일 정산 합은 있는데 월 정산 건이 없으면 월 지급 대사에서 불일치로 잡힌다")
  void 월정산건_누락() {
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 5), 15_000L);

    ReconciliationResult result = resultOf(ReconciliationType.MONTHLY_PAYOUT);

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
    assertThat(result.getMismatchRefs()).isEqualTo("merchantId=1:MISSING");
  }

  @Test
  @DisplayName("월 지급액이 일 정산 확정액의 합과 다르면 월 지급 대사에서 불일치로 잡힌다")
  void 월지급액_불일치() {
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 5), 15_000L);
    monthlyRepository.save(MonthlySettlement.create(MERCHANT, OCTOBER_FIRST, 20_000L, NOW));

    ReconciliationResult result = resultOf(ReconciliationType.MONTHLY_PAYOUT);

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
    assertThat(result.getMismatchRefs()).isEqualTo("merchantId=1:AMOUNT");
  }

  @Test
  @DisplayName("지급 완료인데 지갑 원장에 입금·출금이 없으면 월 지급 대사에서 불일치로 잡힌다")
  void 지급완료_원장없음() {
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 5), 15_000L);
    MonthlySettlement settlement = MonthlySettlement.create(MERCHANT, OCTOBER_FIRST, 15_000L, NOW);
    settlement.markPaid(NOW);
    monthlyRepository.save(settlement);

    ReconciliationResult result = resultOf(ReconciliationType.MONTHLY_PAYOUT);

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
    assertThat(result.getMismatchRefs()).isEqualTo("merchantId=1:LEDGER");
  }

  @Test
  @DisplayName("지급 대기 건은 원장과 비교하지 않는다")
  void 대기건_원장비교_제외() {
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 5), 15_000L);
    monthlyRepository.save(MonthlySettlement.create(MERCHANT, OCTOBER_FIRST, 15_000L, NOW));

    ReconciliationResult result = resultOf(ReconciliationType.MONTHLY_PAYOUT);

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
  }

  @Test
  @DisplayName("기준일이 말일이면 그 달의 월 지급을 대사한다")
  void 말일기준_그달대사() {
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 5), 15_000L);

    ReconciliationResult result =
        reconciliationService.run(LocalDate.of(2026, 10, 31)).stream()
            .filter(r -> r.getType() == ReconciliationType.MONTHLY_PAYOUT)
            .findFirst()
            .orElseThrow();

    assertThat(result.getMismatchRefs()).isEqualTo("merchantId=1:MISSING");
  }

  @Test
  @DisplayName("같은 날짜를 다시 실행하면 결과가 이력으로 쌓인다")
  void 재실행_이력누적() {
    reconciliationService.run(D17);
    reconciliationService.run(D17);

    assertThat(resultRepository.count()).isEqualTo(ReconciliationType.values().length * 2L);
  }

  private ReconciliationResult resultOf(ReconciliationType type) {
    return reconciliationService.run(D17).stream()
        .filter(r -> r.getType() == type)
        .findFirst()
        .orElseThrow();
  }

  private void saveDaily(Long merchantId, LocalDate date, long paymentTotal) {
    dailyRepository.save(
        DailySettlement.create(merchantId, date, paymentTotal, 0L, 0L, date.atTime(1, 0)));
  }

  private void saveCompleted(Long merchantId, long amount, LocalDateTime completedAt) {
    paymentRepository.save(
        Payment.builder()
            .walletId(1L)
            .merchantId(merchantId)
            .idempotencyKey(UUID.randomUUID().toString())
            .amount(amount)
            .walletUsedAmount(amount)
            .cashbackUsedAmount(0L)
            .status(PaymentStatus.COMPLETED)
            .requestedAt(completedAt)
            .completedAt(completedAt)
            .build());
  }
}
