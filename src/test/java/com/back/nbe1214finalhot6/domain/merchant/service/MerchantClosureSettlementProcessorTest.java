package com.back.nbe1214finalhot6.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantClosureDeduction;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantClosureDeductionRepository;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantClosureSettlementProcessor.Outcome;
import com.back.nbe1214finalhot6.domain.reconciliation.service.MonthlyPayoutCheck;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlementStatus;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.MerchantWalletService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// docker compose 의 PostgreSQL 이 켜져 있어야 한다. 테스트 전용 DB(devpay_test)를 사용한다.
@IntegrationTest
@Transactional
class MerchantClosureSettlementProcessorTest {

  // 해지를 신청한 날
  private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

  private long nextOwnerId = 9_300L;

  @Autowired private MerchantClosureSettlementProcessor processor;
  @Autowired private MerchantClosureDeductionRepository deductionRepository;
  @Autowired private MonthlyPayoutCheck monthlyPayoutCheck;
  @Autowired private MerchantRepository merchantRepository;
  @Autowired private MerchantWalletService merchantWalletService;
  @Autowired private MerchantWalletRepository walletRepository;
  @Autowired private MerchantAccountRepository accountRepository;
  @Autowired private DailySettlementRepository dailySettlementRepository;
  @Autowired private MonthlySettlementRepository monthlySettlementRepository;
  @Autowired private MutableClock clock;

  @Test
  @DisplayName("신청일까지의 정산금을 계좌로 지급하고 해지 완료로 바꾼다")
  void 정산_후_해지_완료() {
    Long merchantId = saveClosingMerchant();
    saveDaily(merchantId, DAY.minusDays(1), 30_000, 0, 0);
    saveDaily(merchantId, DAY, 10_000, 0, 0);

    Outcome outcome = processor.settleOne(merchantId, DAY);

    assertThat(outcome).isEqualTo(Outcome.CLOSED);
    assertThat(statusOf(merchantId)).isEqualTo(MerchantStatus.CLOSED);
    MonthlySettlement monthly = onlyMonthly(merchantId);
    assertThat(monthly.getSettlementMonth()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(monthly.getPayoutAmount()).isEqualTo(40_000);
    assertThat(monthly.isPaid()).isTrue();
    assertThat(accountBalance(merchantId)).isEqualTo(40_000);
    assertThat(walletBalance(merchantId)).isZero();
  }

  @Test
  @DisplayName("마지막 일 정산에 남은 부족분은 정산금에서 차감해 지급한다")
  void 부족분_차감() {
    Long merchantId = saveClosingMerchant();
    saveDaily(merchantId, DAY.minusDays(2), 30_000, 0, 0);
    // 취소 10,000원이 매출 없이 들어와 10,000원이 신청일까지 이어진 상태
    saveDaily(merchantId, DAY.minusDays(1), 0, 10_000, 0);
    saveDaily(merchantId, DAY, 0, 0, -10_000);

    Outcome outcome = processor.settleOne(merchantId, DAY);

    assertThat(outcome).isEqualTo(Outcome.CLOSED);
    assertThat(onlyMonthly(merchantId).getPayoutAmount()).isEqualTo(20_000);
    assertThat(accountBalance(merchantId)).isEqualTo(20_000);
  }

  @Test
  @DisplayName("차감한 달과 금액을 남기고, 월 지급 대사가 그 지급을 일치로 본다")
  void 차감액_기록과_대사_일치() {
    Long merchantId = saveClosingMerchant();
    saveDaily(merchantId, DAY.minusDays(2), 30_000, 0, 0);
    saveDaily(merchantId, DAY.minusDays(1), 0, 10_000, 0);
    saveDaily(merchantId, DAY, 0, 0, -10_000);

    processor.settleOne(merchantId, DAY);

    assertThat(deductionRepository.findByMerchantIdOrderBySettlementMonthAsc(merchantId))
        .extracting(MerchantClosureDeduction::getSettlementMonth, MerchantClosureDeduction::getAmount)
        .containsExactly(tuple(LocalDate.of(2026, 11, 1), 10_000L));
    assertThat(monthlyPayoutCheck.check(LocalDate.of(2026, 11, 30)).mismatches()).isEmpty();
  }

  @Test
  @DisplayName("전액 차감되어 월 정산 건이 없는 달도 월 지급 대사에서 불일치로 잡히지 않는다")
  void 전액_차감_대사_일치() {
    Long merchantId = saveClosingMerchant();
    // 10월 매출은 그대로 지급되고, 11월 매출 5,000원은 취소 5,000원으로 전액 차감된다
    saveDaily(merchantId, LocalDate.of(2026, 10, 20), 20_000, 0, 0);
    saveDaily(merchantId, DAY.minusDays(1), 5_000, 0, 0);
    saveDaily(merchantId, DAY, 0, 5_000, 0);

    Outcome outcome = processor.settleOne(merchantId, DAY);

    assertThat(outcome).isEqualTo(Outcome.CLOSED);
    assertThat(monthlyOf(merchantId))
        .extracting(MonthlySettlement::getSettlementMonth)
        .containsExactly(LocalDate.of(2026, 10, 1));
    assertThat(deductionRepository.findByMerchantIdOrderBySettlementMonthAsc(merchantId))
        .extracting(MerchantClosureDeduction::getSettlementMonth, MerchantClosureDeduction::getAmount)
        .containsExactly(tuple(LocalDate.of(2026, 11, 1), 5_000L));
    assertThat(monthlyPayoutCheck.check(LocalDate.of(2026, 11, 30)).mismatches()).isEmpty();
  }

  @Test
  @DisplayName("부족분이 정산금보다 많으면 아무것도 바꾸지 않고 보류한다")
  void 부족분이_많으면_보류() {
    Long merchantId = saveClosingMerchant();
    saveDaily(merchantId, DAY.minusDays(1), 5_000, 0, 0);
    saveDaily(merchantId, DAY, 0, 20_000, 0);

    Outcome outcome = processor.settleOne(merchantId, DAY);

    assertThat(outcome).isEqualTo(Outcome.HELD);
    assertThat(statusOf(merchantId)).isEqualTo(MerchantStatus.CLOSING);
    assertThat(monthlySettlementRepository.findAll())
        .noneMatch(monthly -> monthly.getMerchantId().equals(merchantId));
    assertThat(deductionRepository.findByMerchantIdOrderBySettlementMonthAsc(merchantId)).isEmpty();
    assertThat(accountBalance(merchantId)).isZero();
  }

  @Test
  @DisplayName("월 정산 건이 없는 여러 달을 달마다 건으로 만들어 모두 지급한다")
  void 여러_달_정산() {
    Long merchantId = saveClosingMerchant();
    saveDaily(merchantId, LocalDate.of(2026, 10, 20), 10_000, 0, 0);
    saveDaily(merchantId, DAY, 5_000, 0, 0);

    processor.settleOne(merchantId, DAY);

    List<MonthlySettlement> rows = monthlyOf(merchantId);
    assertThat(rows)
        .extracting(MonthlySettlement::getSettlementMonth, MonthlySettlement::getPayoutAmount)
        .containsExactlyInAnyOrder(
            tuple(LocalDate.of(2026, 10, 1), 10_000L), tuple(LocalDate.of(2026, 11, 1), 5_000L));
    assertThat(rows).allMatch(MonthlySettlement::isPaid);
    assertThat(accountBalance(merchantId)).isEqualTo(15_000);
  }

  @Test
  @DisplayName("이미 월 정산 건이 있는 달은 다시 만들지 않는다")
  void 이미_정산된_달_제외() {
    Long merchantId = saveClosingMerchant();
    MonthlySettlement october =
        MonthlySettlement.create(
            merchantId, LocalDate.of(2026, 10, 1), 10_000, LocalDateTime.now(clock));
    october.markPaid(LocalDateTime.now(clock));
    monthlySettlementRepository.save(october);
    saveDaily(merchantId, LocalDate.of(2026, 10, 20), 10_000, 0, 0);
    saveDaily(merchantId, DAY, 5_000, 0, 0);

    processor.settleOne(merchantId, DAY);

    assertThat(monthlyOf(merchantId)).hasSize(2);
    assertThat(accountBalance(merchantId)).isEqualTo(5_000);
  }

  @Test
  @DisplayName("신청일 다음 날의 정산은 포함하지 않는다")
  void 신청일_이후_정산_제외() {
    Long merchantId = saveClosingMerchant();
    saveDaily(merchantId, DAY, 10_000, 0, 0);
    saveDaily(merchantId, DAY.plusDays(1), 7_000, 0, 0);

    processor.settleOne(merchantId, DAY);

    assertThat(onlyMonthly(merchantId).getPayoutAmount()).isEqualTo(10_000);
  }

  @Test
  @DisplayName("이미 만들어진 지급 대기 건도 함께 지급한다")
  void 지급_대기_건_함께_지급() {
    Long merchantId = saveClosingMerchant();
    monthlySettlementRepository.save(
        MonthlySettlement.create(
            merchantId, LocalDate.of(2026, 10, 1), 8_000, LocalDateTime.now(clock)));

    Outcome outcome = processor.settleOne(merchantId, DAY);

    assertThat(outcome).isEqualTo(Outcome.CLOSED);
    assertThat(monthlyOf(merchantId))
        .allMatch(monthly -> monthly.getStatus() == MonthlySettlementStatus.PAID);
    assertThat(accountBalance(merchantId)).isEqualTo(8_000);
  }

  @Test
  @DisplayName("이미 해지된 가맹점은 다시 처리하지 않는다")
  void 이미_해지된_가맹점() {
    Long merchantId = saveClosingMerchant();
    saveDaily(merchantId, DAY, 10_000, 0, 0);
    merchantRepository.updateStatusIfCurrent(
        merchantId, MerchantStatus.CLOSING, MerchantStatus.CLOSED);

    Outcome outcome = processor.settleOne(merchantId, DAY);

    assertThat(outcome).isEqualTo(Outcome.ALREADY);
    assertThat(monthlyOf(merchantId)).isEmpty();
    assertThat(accountBalance(merchantId)).isZero();
  }

  private Long saveClosingMerchant() {
    Long merchantId =
        merchantRepository
            .save(Merchant.create(nextOwnerId++, "해지 정산 가맹점", "인천광역시 남동구 1", true))
            .getId();
    merchantWalletService.createFor(merchantId);
    merchantRepository.updateStatusIfCurrent(
        merchantId, MerchantStatus.ACTIVE, MerchantStatus.CLOSING);
    return merchantId;
  }

  private void saveDaily(
      Long merchantId, LocalDate date, long paymentTotal, long cancelTotal, long carryIn) {
    dailySettlementRepository.save(
        DailySettlement.create(
            merchantId, date, paymentTotal, cancelTotal, carryIn, LocalDateTime.now(clock)));
  }

  private MerchantStatus statusOf(Long merchantId) {
    return merchantRepository.findStatusById(merchantId).orElseThrow();
  }

  private List<MonthlySettlement> monthlyOf(Long merchantId) {
    return monthlySettlementRepository.findAll().stream()
        .filter(monthly -> monthly.getMerchantId().equals(merchantId))
        .toList();
  }

  private MonthlySettlement onlyMonthly(Long merchantId) {
    List<MonthlySettlement> rows = monthlyOf(merchantId);
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }

  private long accountBalance(Long merchantId) {
    return accountRepository.findByMerchantId(merchantId).orElseThrow().getBalance();
  }

  private long walletBalance(Long merchantId) {
    return walletRepository.findByMerchantId(merchantId).orElseThrow().getBalance();
  }
}
