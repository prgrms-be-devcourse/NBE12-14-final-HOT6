package com.back.nbe1214finalhot6.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.dto.ClosurePreviewResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.ClosureResponse;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantClosureRepository;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// docker compose 의 PostgreSQL 이 켜져 있어야 한다. 테스트 전용 DB(devpay_test)를 사용한다.
@IntegrationTest
@Transactional
class MerchantClosureServiceTest {

  private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
  private static final Long OWNER_ID = 9_001L;

  @Autowired private MerchantClosureService closureService;
  @Autowired private MerchantRepository merchantRepository;
  @Autowired private MerchantClosureRepository closureRepository;
  @Autowired private DailySettlementRepository dailySettlementRepository;
  @Autowired private MonthlySettlementRepository monthlySettlementRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private MutableClock clock;

  @Test
  @DisplayName("갚을 금액이 없으면 해지를 신청할 수 있고 가맹점은 해지 진행 중이 된다")
  void 해지_신청_성공() {
    Long merchantId = saveMerchant();

    ClosureResponse response = closureService.request(merchantId);

    assertThat(response.status()).isEqualTo(MerchantStatus.CLOSING);
    assertThat(response.requestedAt()).isEqualTo(LocalDateTime.now(clock));
    assertThat(merchantRepository.findStatusById(merchantId)).contains(MerchantStatus.CLOSING);
    assertThat(closureRepository.findByMerchantId(merchantId)).isPresent();
  }

  @Test
  @DisplayName("이미 해지를 신청했으면 다시 신청할 수 없다 (MER-003)")
  void 중복_신청_거절() {
    Long merchantId = saveMerchant();
    closureService.request(merchantId);

    assertThatThrownBy(() -> closureService.request(merchantId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_CLOSING));
    assertThat(closureRepository.findByMerchantId(merchantId)).isPresent();
  }

  @Test
  @DisplayName("해지가 끝난 가맹점은 신청할 수 없다 (MER-004)")
  void 해지된_가맹점_거절() {
    Long merchantId = saveMerchant();
    merchantRepository.updateStatusIfCurrent(
        merchantId, MerchantStatus.ACTIVE, MerchantStatus.CLOSED);

    assertThatThrownBy(() -> closureService.request(merchantId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_CLOSED));
  }

  @Test
  @DisplayName("없는 가맹점이면 신청과 미리보기 모두 MER-001 이다")
  void 없는_가맹점() {
    assertThatThrownBy(() -> closureService.request(Long.MAX_VALUE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_NOT_FOUND));
    assertThatThrownBy(() -> closureService.preview(Long.MAX_VALUE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_NOT_FOUND));
  }

  @Test
  @DisplayName("정산금으로 갚을 금액을 감당하지 못하면 신청이 거절되고 상태는 그대로다 (MER-005)")
  void 부족분이_있으면_거절() {
    Long merchantId = saveMerchant();
    // 취소 5,000원이 그날 매출보다 많아 5,000원을 다음으로 넘긴 상태
    saveDaily(merchantId, DAY, 0, 5_000, 0);

    assertThatThrownBy(() -> closureService.request(merchantId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(MerchantErrorCode.MERCHANT_CLOSURE_SHORTFALL));
    assertThat(merchantRepository.findStatusById(merchantId)).contains(MerchantStatus.ACTIVE);
    assertThat(closureRepository.findByMerchantId(merchantId)).isEmpty();
  }

  @Test
  @DisplayName("계좌 잔액이 충분해도 정산금이 갚을 금액에 모자라면 거절한다 (MER-005)")
  void 계좌_잔액은_상계에_쓰지_않는다() {
    Long merchantId = saveMerchant();
    saveDaily(merchantId, DAY, 0, 5_000, 0);
    Account account = accountRepository.findByUserId(OWNER_ID).orElseThrow();
    account.credit(100_000);
    accountRepository.saveAndFlush(account);

    assertThatThrownBy(() -> closureService.request(merchantId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(MerchantErrorCode.MERCHANT_CLOSURE_SHORTFALL));
    assertThat(accountRepository.findByUserId(OWNER_ID).orElseThrow().getBalance())
        .isEqualTo(100_000);
  }

  @Test
  @DisplayName("월 정산 건이 아직 없는 정산금이 갚을 금액 이상이면 신청할 수 있다")
  void 정산금으로_감당() {
    Long merchantId = saveMerchant();
    // 하루 전 매출 5,000원이 확정되고, 다음 날 취소 5,000원이 매출 없이 들어와 5,000원을 넘긴 상태
    saveDaily(merchantId, DAY.minusDays(1), 5_000, 0, 0);
    saveDaily(merchantId, DAY, 0, 5_000, 0);

    ClosureResponse response = closureService.request(merchantId);

    assertThat(response.status()).isEqualTo(MerchantStatus.CLOSING);
  }

  @Test
  @DisplayName("미리보기는 지급 대기 월 정산과 월 정산에 묶이지 않은 일 정산을 정산금으로 합친다")
  void 미리보기_정산금_합계() {
    Long merchantId = saveMerchant();
    LocalDateTime now = LocalDateTime.now(clock);
    // 10월 정산은 지급 대기 20,000원, 11월 일 정산은 아직 월 정산 전 30,000원
    monthlySettlementRepository.save(
        MonthlySettlement.create(merchantId, LocalDate.of(2026, 10, 1), 20_000, now));
    saveDaily(merchantId, DAY, 30_000, 0, 0);

    ClosurePreviewResponse preview = closureService.preview(merchantId);

    assertThat(preview.status()).isEqualTo(MerchantStatus.ACTIVE);
    assertThat(preview.unpaidAmount()).isEqualTo(50_000);
    // 지급 대기 20,000원은 이미 건이 만들어져 차감에 쓸 수 없고, 월 정산 전 30,000원만 쓸 수 있다
    assertThat(preview.offsettableAmount()).isEqualTo(30_000);
    assertThat(preview.carryDebt()).isZero();
    assertThat(preview.closable()).isTrue();
    assertThat(preview.shortfall()).isZero();
  }

  @Test
  @DisplayName("이미 월 정산에 묶인 달의 일 정산은 정산금에 다시 더하지 않는다")
  void 월정산에_묶인_일정산_중복_제외() {
    Long merchantId = saveMerchant();
    LocalDateTime now = LocalDateTime.now(clock);
    // 11월 월 정산이 이미 만들어져 11월 일 정산은 그 안에 들어 있다
    monthlySettlementRepository.save(
        MonthlySettlement.create(merchantId, LocalDate.of(2026, 11, 1), 30_000, now));
    saveDaily(merchantId, DAY, 30_000, 0, 0);

    ClosurePreviewResponse preview = closureService.preview(merchantId);

    assertThat(preview.unpaidAmount()).isEqualTo(30_000);
  }

  @Test
  @DisplayName("미리보기는 갚을 금액과 부족 금액을 보여 주고 상태를 바꾸지 않는다")
  void 미리보기_부족분() {
    Long merchantId = saveMerchant();
    saveDaily(merchantId, DAY, 0, 8_000, 0);
    Account account = accountRepository.findByUserId(OWNER_ID).orElseThrow();
    account.credit(3_000);
    accountRepository.saveAndFlush(account);

    ClosurePreviewResponse preview = closureService.preview(merchantId);

    assertThat(preview.carryDebt()).isEqualTo(8_000);
    assertThat(preview.accountBalance()).isEqualTo(3_000);
    assertThat(preview.closable()).isFalse();
    assertThat(preview.shortfall()).isEqualTo(8_000);
    assertThat(merchantRepository.findStatusById(merchantId)).contains(MerchantStatus.ACTIVE);
  }

  private Long saveMerchant() {
    Merchant merchant =
        merchantRepository.save(Merchant.create(OWNER_ID, "해지 테스트 가맹점", "인천광역시 남동구 1", true));
    // 점주의 모의계좌가 정산 계좌다
    accountRepository.save(Account.createEmpty(OWNER_ID, LocalDateTime.now(clock)));
    return merchant.getId();
  }

  private void saveDaily(
      Long merchantId, LocalDate date, long paymentTotal, long cancelTotal, long carryIn) {
    dailySettlementRepository.save(
        DailySettlement.create(
            merchantId, date, paymentTotal, cancelTotal, carryIn, LocalDateTime.now(clock)));
  }
}
