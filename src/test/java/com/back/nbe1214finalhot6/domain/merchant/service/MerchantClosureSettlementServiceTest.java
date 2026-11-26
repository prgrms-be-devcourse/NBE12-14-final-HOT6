package com.back.nbe1214finalhot6.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.merchant.dto.ClosureSettlementRunResult;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantClosure;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantClosureRepository;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.service.MonthlySettlementService;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.MerchantWalletService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// docker compose 의 PostgreSQL 이 켜져 있어야 한다. 테스트 전용 DB(devpay_test)를 사용한다.
@IntegrationTest
@Transactional
class MerchantClosureSettlementServiceTest {

  private long nextOwnerId = 9_400L;

  @Autowired private MerchantClosureSettlementService settlementService;
  @Autowired private MonthlySettlementService monthlySettlementService;
  @Autowired private MerchantRepository merchantRepository;
  @Autowired private MerchantClosureRepository closureRepository;
  @Autowired private MerchantWalletService merchantWalletService;
  @Autowired private MerchantAccountRepository accountRepository;
  @Autowired private DailySettlementRepository dailySettlementRepository;
  @Autowired private MonthlySettlementRepository monthlySettlementRepository;
  @Autowired private MutableClock clock;

  @Test
  @DisplayName("신청한 날이 아직 지나지 않았으면 대기로 두고 해지하지 않는다")
  void 신청일이_지나지_않으면_대기() {
    Long merchantId = saveClosingMerchant(LocalDateTime.now(clock));

    ClosureSettlementRunResult result = settlementService.run();

    assertThat(result.waiting()).isEqualTo(1);
    assertThat(result.closed()).isZero();
    assertThat(statusOf(merchantId)).isEqualTo(MerchantStatus.CLOSING);
  }

  @Test
  @DisplayName("신청일보다 늦게 시작된 일 정산뿐이면 매출이 빠질 수 있어 미루고 해지하지 않는다")
  void 일정산_이력이_신청일보다_늦으면_미룸() {
    LocalDate today = LocalDate.now(clock);
    Long merchantId = saveClosingMerchant(today.minusDays(5).atTime(10, 0));
    saveDaily(merchantId, today.minusDays(2), 10_000);

    ClosureSettlementRunResult result = settlementService.run();

    assertThat(result.dailyIncomplete()).isEqualTo(1);
    assertThat(result.waiting()).isZero();
    assertThat(result.closed()).isZero();
    assertThat(statusOf(merchantId)).isEqualTo(MerchantStatus.CLOSING);
  }

  @Test
  @DisplayName("신청일이 지났으면 정산하고 해지 완료로 바꾼다")
  void 신청일이_지나면_정산_후_해지() {
    LocalDate today = LocalDate.now(clock);
    LocalDate closureDate = today.minusDays(3);
    Long merchantId = saveClosingMerchant(closureDate.atTime(10, 0));
    saveDaily(merchantId, closureDate.minusDays(1), 30_000);
    saveDaily(merchantId, closureDate, 10_000);

    ClosureSettlementRunResult result = settlementService.run();

    assertThat(result.closed()).isEqualTo(1);
    assertThat(result.held()).isZero();
    assertThat(statusOf(merchantId)).isEqualTo(MerchantStatus.CLOSED);
    assertThat(accountRepository.findByMerchantId(merchantId).orElseThrow().getBalance())
        .isEqualTo(40_000);
  }

  @Test
  @DisplayName("같은 실행을 두 번 해도 정산금은 한 번만 지급된다")
  void 두_번_실행해도_한_번만_지급() {
    LocalDate closureDate = LocalDate.now(clock).minusDays(3);
    Long merchantId = saveClosingMerchant(closureDate.atTime(10, 0));
    saveDaily(merchantId, closureDate, 10_000);

    settlementService.run();
    ClosureSettlementRunResult second = settlementService.run();

    assertThat(second.closed()).isZero();
    assertThat(accountRepository.findByMerchantId(merchantId).orElseThrow().getBalance())
        .isEqualTo(10_000);
  }

  @Test
  @DisplayName("부족분이 정산금보다 많으면 보류 목록에 담고 해지 진행 중으로 남긴다")
  void 보류_목록() {
    LocalDate closureDate = LocalDate.now(clock).minusDays(3);
    Long merchantId = saveClosingMerchant(closureDate.atTime(10, 0));
    saveDaily(merchantId, closureDate.minusDays(1), 5_000);
    dailySettlementRepository.save(
        DailySettlement.create(merchantId, closureDate, 0, 20_000, 0, LocalDateTime.now(clock)));

    ClosureSettlementRunResult result = settlementService.run();

    assertThat(result.held()).isEqualTo(1);
    assertThat(result.heldMerchantIds()).containsExactly(merchantId);
    assertThat(statusOf(merchantId)).isEqualTo(MerchantStatus.CLOSING);
  }

  @Test
  @DisplayName("해지 진행 중인 가맹점은 일반 월 정산에서 빠져 중복 지급되지 않는다")
  void 일반_월정산에서_제외() {
    YearMonth lastMonth = YearMonth.now(clock).minusMonths(1);
    Long merchantId = saveClosingMerchant(LocalDateTime.now(clock));
    saveDaily(merchantId, lastMonth.atDay(5), 10_000);

    MonthlySettlementRunResult result = monthlySettlementService.createMissing(lastMonth);

    assertThat(result.created()).isZero();
    assertThat(monthlySettlementRepository.findAll())
        .noneMatch(monthly -> monthly.getMerchantId().equals(merchantId));
  }

  private Long saveClosingMerchant(LocalDateTime requestedAt) {
    Long merchantId =
        merchantRepository
            .save(Merchant.create(nextOwnerId++, "해지 정산 서비스 가맹점", "인천광역시 남동구 1", true))
            .getId();
    merchantWalletService.createFor(merchantId);
    merchantRepository.updateStatusIfCurrent(
        merchantId, MerchantStatus.ACTIVE, MerchantStatus.CLOSING);
    closureRepository.save(MerchantClosure.create(merchantId, requestedAt));
    return merchantId;
  }

  private void saveDaily(Long merchantId, LocalDate date, long paymentTotal) {
    dailySettlementRepository.save(
        DailySettlement.create(merchantId, date, paymentTotal, 0, 0, LocalDateTime.now(clock)));
  }

  private MerchantStatus statusOf(Long merchantId) {
    return merchantRepository.findStatusById(merchantId).orElseThrow();
  }
}
