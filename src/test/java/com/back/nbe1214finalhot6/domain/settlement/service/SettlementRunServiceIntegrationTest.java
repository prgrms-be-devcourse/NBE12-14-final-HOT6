package com.back.nbe1214finalhot6.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.dto.SettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.MerchantWalletService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 테스트 시계는 2026-11-18 09:00 KST 에서 시작하므로 어제는 11/17, 지난달은 10월이다.
@IntegrationTest
class SettlementRunServiceIntegrationTest {

  private static final Long MERCHANT = 1L;
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 0, 30);

  @Autowired SettlementRunService runService;
  @Autowired DailySettlementRepository dailyRepository;
  @Autowired MonthlySettlementRepository monthlyRepository;
  @Autowired PaymentRepository paymentRepository;
  @Autowired MerchantWalletService walletService;
  @Autowired MerchantWalletRepository walletRepository;
  @Autowired MerchantAccountRepository accountRepository;
  @Autowired MerchantWalletTransactionRepository transactionRepository;

  @AfterEach
  void cleanUp() {
    transactionRepository.deleteAll();
    monthlyRepository.deleteAll();
    dailyRepository.deleteAll();
    paymentRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
  }

  @Test
  @DisplayName("수동 일 정산은 마지막 정산일 이후 날짜를 건너뛰지 않고 채워서 전일 이월이 이어진다")
  void 일정산_중간날짜_채움() {
    // 11/10 에 3만원이 부족해 다음 날로 넘어갔고, 11/11~11/13 에는 매출이 없다
    dailyRepository.save(
        DailySettlement.create(MERCHANT, LocalDate.of(2026, 11, 10), 0L, 30_000L, 0L, NOW));
    saveCompleted(50_000L, LocalDate.of(2026, 11, 14).atTime(10, 0));

    SettlementRunResult result = runService.runDaily(LocalDate.of(2026, 11, 14));

    assertThat(result.date()).isEqualTo(LocalDate.of(2026, 11, 14));
    assertThat(result.failed()).isZero();
    DailySettlement d14 = find(LocalDate.of(2026, 11, 14));
    assertThat(d14.getCarryIn()).isEqualTo(-30_000L);
    assertThat(d14.getConfirmedAmount()).isEqualTo(20_000L);
    assertThat(
            dailyRepository.findByMerchantIdAndSettlementDate(MERCHANT, LocalDate.of(2026, 11, 12)))
        .isPresent();
  }

  @Test
  @DisplayName("수동 월 정산은 월말까지 덜 끝난 일 정산을 먼저 채운 합계로 지급한다")
  void 월정산_월말일정산_채운뒤_지급() {
    walletService.createFor(MERCHANT);
    dailyRepository.save(
        DailySettlement.create(MERCHANT, LocalDate.of(2026, 10, 5), 15_000L, 0L, 0L, NOW));
    dailyRepository.save(
        DailySettlement.create(MERCHANT, LocalDate.of(2026, 10, 30), 0L, 0L, 0L, NOW));
    saveCompleted(5_000L, LocalDate.of(2026, 10, 31).atTime(10, 0));

    MonthlySettlementRunResult result = runService.runMonthly(YearMonth.of(2026, 10));

    assertThat(result.created()).isEqualTo(1);
    assertThat(result.paid()).isEqualTo(1);
    MonthlySettlement settlement = monthlyRepository.findAll().getFirst();
    assertThat(settlement.getPayoutAmount()).isEqualTo(20_000L);
  }

  private DailySettlement find(LocalDate date) {
    return dailyRepository.findByMerchantIdAndSettlementDate(MERCHANT, date).orElseThrow();
  }

  private void saveCompleted(long amount, LocalDateTime completedAt) {
    paymentRepository.save(
        Payment.builder()
            .walletId(1L)
            .merchantId(MERCHANT)
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
