package com.back.nbe1214finalhot6.domain.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantClosure;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantClosureDeduction;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantClosureDeductionRepository;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantClosureRepository;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantClosureSettlementProcessor;
import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// docker compose 의 PostgreSQL 이 켜져 있어야 한다. 테스트 전용 DB(devpay_test)를 사용한다.
// 다른 테스트가 남긴 해지 가맹점과 섞이지 않도록, 결과는 이 테스트가 만든 가맹점 번호로만 확인한다.
@IntegrationTest
@Transactional
class MerchantClosureCheckTest {

  // 해지를 신청한 날
  private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 11, 17);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 9, 0);

  private long nextOwnerId = 9_701L;

  @Autowired private MerchantClosureCheck check;
  @Autowired private MerchantClosureSettlementProcessor processor;
  @Autowired private MerchantRepository merchantRepository;
  @Autowired private MerchantClosureRepository closureRepository;
  @Autowired private MerchantClosureDeductionRepository deductionRepository;
  @Autowired private DailySettlementRepository dailyRepository;
  @Autowired private MonthlySettlementRepository monthlyRepository;
  @Autowired private AccountRepository accountRepository;

  @Test
  @DisplayName("대사 종류는 MERCHANT_CLOSURE 다")
  void 대사_종류() {
    assertThat(check.type()).isEqualTo(ReconciliationType.MERCHANT_CLOSURE);
  }

  @Test
  @DisplayName("해지 정산을 정상으로 끝낸 가맹점은 부족분을 차감했어도 일치한다")
  void 정상_해지는_일치() {
    Long merchantId = saveClosingMerchant();
    saveDaily(merchantId, DAY.minusDays(2), 30_000, 0, 0);
    saveDaily(merchantId, DAY.minusDays(1), 0, 10_000, 0);
    saveDaily(merchantId, DAY, 0, 0, -10_000);
    processor.settleOne(merchantId, DAY);

    ReconciliationOutcome outcome = check.check(CHECK_DATE);

    assertThat(outcome.mismatches()).noneMatch(ref -> ref.startsWith(ref(merchantId)));
    assertThat(outcome.checkedCount()).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("지급이 끝나지 않은 월 정산 건이 남아 있으면 PENDING 이다")
  void 대기건_남음() {
    Long merchantId = saveClosedMerchant();
    saveDaily(merchantId, DAY, 10_000, 0, 0);
    monthlyRepository.save(MonthlySettlement.create(merchantId, DAY.withDayOfMonth(1), 10_000, NOW));

    assertThat(check.check(CHECK_DATE).mismatches()).contains(ref(merchantId) + ":PENDING");
  }

  @Test
  @DisplayName("해지 신청일 뒤에 취소가 일 정산에 반영됐으면 청산되지 않은 돈이 있어 AFTER 다")
  void 신청일_뒤_취소() {
    Long merchantId = saveClosedMerchant();
    saveDaily(merchantId, DAY, 30_000, 0, 0);
    savePaid(merchantId, 30_000);
    // 신청 뒤에 취소 5,000원이 들어왔다
    saveDaily(merchantId, DAY.plusDays(2), 0, 5_000, 0);

    assertThat(check.check(CHECK_DATE).mismatches()).contains(ref(merchantId) + ":AFTER");
  }

  @Test
  @DisplayName("신청일 뒤에 부족분만 넘긴 행은 결제·취소가 없으므로 AFTER 가 아니다")
  void 신청일_뒤_이월만() {
    Long merchantId = saveClosingMerchant();
    saveDaily(merchantId, DAY.minusDays(2), 30_000, 0, 0);
    saveDaily(merchantId, DAY.minusDays(1), 0, 10_000, 0);
    saveDaily(merchantId, DAY, 0, 0, -10_000);
    // 해지 정산 전날까지 부족분이 이월된 행
    saveDaily(merchantId, DAY.plusDays(1), 0, 0, -10_000);
    processor.settleOne(merchantId, DAY);

    assertThat(check.check(CHECK_DATE).mismatches())
        .noneMatch(ref -> ref.startsWith(ref(merchantId) + ":"));
  }

  @Test
  @DisplayName("차감액의 합이 신청일의 부족분과 다르면 DEDUCTION 이다")
  void 차감액_불일치() {
    Long merchantId = saveClosedMerchant();
    saveDaily(merchantId, DAY.minusDays(1), 30_000, 0, 0);
    // 신청일에 10,000원이 부족했는데 5,000원만 차감한 상태
    saveDaily(merchantId, DAY, 0, 10_000, 0);
    deductionRepository.save(
        MerchantClosureDeduction.create(merchantId, DAY.withDayOfMonth(1), 5_000, NOW));
    savePaid(merchantId, 25_000);

    assertThat(check.check(CHECK_DATE).mismatches()).contains(ref(merchantId) + ":DEDUCTION");
  }

  @Test
  @DisplayName("확정액 합에서 차감액을 뺀 값이 지급액 합과 다르면 PAYOUT 이다")
  void 지급액_불일치() {
    Long merchantId = saveClosedMerchant();
    saveDaily(merchantId, DAY, 30_000, 0, 0);
    savePaid(merchantId, 20_000);

    assertThat(check.check(CHECK_DATE).mismatches()).contains(ref(merchantId) + ":PAYOUT");
  }

  @Test
  @DisplayName("해지 신청 기록이 없는 해지 완료 가맹점은 REQUEST 다")
  void 신청기록_없음() {
    Long merchantId =
        merchantRepository
            .save(Merchant.create(nextOwnerId++, "기록 없는 가맹점", "인천광역시 남동구 1", true))
            .getId();
    merchantRepository.updateStatusIfCurrent(merchantId, MerchantStatus.ACTIVE, MerchantStatus.CLOSING);
    merchantRepository.updateStatusIfCurrent(merchantId, MerchantStatus.CLOSING, MerchantStatus.CLOSED);

    assertThat(check.check(CHECK_DATE).mismatches()).contains(ref(merchantId) + ":REQUEST");
  }

  @Test
  @DisplayName("영업 중이거나 해지 진행 중인 가맹점은 정산이 덜 끝났어도 대상이 아니다")
  void 해지완료만_대상() {
    Long active =
        merchantRepository
            .save(Merchant.create(nextOwnerId++, "영업 가맹점", "인천광역시 남동구 1", true))
            .getId();
    Long closing = saveClosingMerchant();
    for (Long merchantId : new Long[] {active, closing}) {
      saveDaily(merchantId, DAY, 10_000, 0, 0);
      monthlyRepository.save(
          MonthlySettlement.create(merchantId, DAY.withDayOfMonth(1), 10_000, NOW));
    }

    ReconciliationOutcome outcome = check.check(CHECK_DATE);

    assertThat(outcome.mismatches())
        .noneMatch(ref -> ref.startsWith(ref(active) + ":") || ref.startsWith(ref(closing) + ":"));
  }

  private Long saveClosingMerchant() {
    long ownerId = nextOwnerId++;
    Long merchantId =
        merchantRepository
            .save(Merchant.create(ownerId, "해지 대사 가맹점", "인천광역시 남동구 1", true))
            .getId();
    accountRepository.save(Account.createEmpty(ownerId, NOW));
    merchantRepository.updateStatusIfCurrent(merchantId, MerchantStatus.ACTIVE, MerchantStatus.CLOSING);
    closureRepository.save(MerchantClosure.create(merchantId, DAY.atTime(10, 0)));
    return merchantId;
  }

  // 대사가 잡아야 할 상태를 직접 만들기 위해 해지 정산을 거치지 않고 CLOSED 로 바꾼다
  private Long saveClosedMerchant() {
    Long merchantId = saveClosingMerchant();
    merchantRepository.updateStatusIfCurrent(merchantId, MerchantStatus.CLOSING, MerchantStatus.CLOSED);
    return merchantId;
  }

  private void saveDaily(
      Long merchantId, LocalDate date, long paymentTotal, long cancelTotal, long carryIn) {
    dailyRepository.save(
        DailySettlement.create(merchantId, date, paymentTotal, cancelTotal, carryIn, NOW));
  }

  private void savePaid(Long merchantId, long payout) {
    MonthlySettlement monthly =
        MonthlySettlement.create(merchantId, DAY.withDayOfMonth(1), payout, NOW);
    monthly.markPaid(NOW);
    monthlyRepository.save(monthly);
  }

  private String ref(Long merchantId) {
    return "merchantId=" + merchantId;
  }
}
