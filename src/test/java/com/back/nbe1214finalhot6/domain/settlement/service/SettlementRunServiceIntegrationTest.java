package com.back.nbe1214finalhot6.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.dto.SettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 테스트 시계는 2026-11-18 09:00 KST 에서 시작하므로 어제는 11/17, 지난달은 10월이다.
@IntegrationTest
class SettlementRunServiceIntegrationTest {

  // 월 정산금을 받을 점주. 가맹점은 테스트마다 이 점주로 만든다
  private static final Long OWNER = 8_301L;
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 0, 30);

  @Autowired SettlementRunService runService;
  @Autowired DailySettlementRepository dailyRepository;
  @Autowired MonthlySettlementRepository monthlyRepository;
  @Autowired PaymentRepository paymentRepository;
  @Autowired MerchantRepository merchantRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  private Long merchantId;

  @BeforeEach
  void saveMerchant() {
    merchantId =
        merchantRepository.save(Merchant.create(OWNER, "수동 정산 가맹점", "인천광역시 남동구 1", true)).getId();
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update(
        "delete from account_transaction where account_id in (select id from account where user_id = ?)",
        OWNER);
    jdbcTemplate.update("delete from account where user_id = ?", OWNER);
    monthlyRepository.deleteAll();
    dailyRepository.deleteAll();
    paymentRepository.deleteAll();
    merchantRepository.deleteById(merchantId);
  }

  @Test
  @DisplayName("수동 일 정산은 마지막 정산일 이후 날짜를 건너뛰지 않고 채워서 전일 이월이 이어진다")
  void 일정산_중간날짜_채움() {
    // 11/10 에 3만원이 부족해 다음 날로 넘어갔고, 11/11~11/13 에는 매출이 없다
    dailyRepository.save(
        DailySettlement.create(merchantId, LocalDate.of(2026, 11, 10), 0L, 30_000L, 0L, NOW));
    saveCompleted(50_000L, LocalDate.of(2026, 11, 14).atTime(10, 0));

    SettlementRunResult result = runService.runDaily(LocalDate.of(2026, 11, 14));

    assertThat(result.date()).isEqualTo(LocalDate.of(2026, 11, 14));
    assertThat(result.failed()).isZero();
    DailySettlement d14 = find(LocalDate.of(2026, 11, 14));
    assertThat(d14.getCarryIn()).isEqualTo(-30_000L);
    assertThat(d14.getConfirmedAmount()).isEqualTo(20_000L);
    assertThat(
            dailyRepository.findByMerchantIdAndSettlementDate(merchantId, LocalDate.of(2026, 11, 12)))
        .isPresent();
  }

  @Test
  @DisplayName("수동 월 정산은 월말까지 덜 끝난 일 정산을 먼저 채운 합계로 지급한다")
  void 월정산_월말일정산_채운뒤_지급() {
    accountRepository.save(Account.createEmpty(OWNER, LocalDateTime.now()));
    dailyRepository.save(
        DailySettlement.create(merchantId, LocalDate.of(2026, 10, 5), 15_000L, 0L, 0L, NOW));
    dailyRepository.save(
        DailySettlement.create(merchantId, LocalDate.of(2026, 10, 30), 0L, 0L, 0L, NOW));
    saveCompleted(5_000L, LocalDate.of(2026, 10, 31).atTime(10, 0));

    MonthlySettlementRunResult result = runService.runMonthly(YearMonth.of(2026, 10));

    assertThat(result.created()).isEqualTo(1);
    assertThat(result.paid()).isEqualTo(1);
    MonthlySettlement settlement = monthlyRepository.findAll().getFirst();
    assertThat(settlement.getPayoutAmount()).isEqualTo(20_000L);
  }

  private DailySettlement find(LocalDate date) {
    return dailyRepository.findByMerchantIdAndSettlementDate(merchantId, date).orElseThrow();
  }

  private void saveCompleted(long amount, LocalDateTime completedAt) {
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
