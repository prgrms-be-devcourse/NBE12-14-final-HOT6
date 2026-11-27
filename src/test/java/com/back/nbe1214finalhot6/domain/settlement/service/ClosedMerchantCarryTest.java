package com.back.nbe1214finalhot6.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.reconciliation.service.SettlementCarryCheck;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 해지가 끝난 가맹점은 해지 정산이 부족분을 차감액으로 청산했으므로, 일 정산이 그 부족분을 다음 날로 계속 넘기지 않는다. 9번 대사도 그 가맹점의 다음 날
 * 장부가 없는 것을 불일치로 보지 않는다.
 *
 * <p>테스트 시계는 2026-11-18 이라 11/12 를 정산할 수 있다.
 */
@IntegrationTest
@Transactional
class ClosedMerchantCarryTest {

  private static final LocalDate YESTERDAY = LocalDate.of(2026, 11, 11);
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 9, 0);

  private long nextOwnerId = 9_901L;

  @Autowired private DailySettlementService dailySettlementService;
  @Autowired private SettlementCarryCheck settlementCarryCheck;
  @Autowired private DailySettlementRepository dailyRepository;
  @Autowired private MerchantRepository merchantRepository;

  @Test
  @DisplayName("해지가 끝난 가맹점의 부족분은 다음 날로 넘기지 않고, 해지 진행 중인 가맹점은 계속 넘긴다")
  void 해지완료_가맹점은_이월_중단() {
    Long closed = saveMerchantWithCarry(MerchantStatus.CLOSED);
    Long closing = saveMerchantWithCarry(MerchantStatus.CLOSING);

    dailySettlementService.settle(TODAY);

    assertThat(dailyRepository.findByMerchantIdAndSettlementDate(closed, TODAY)).isEmpty();
    assertThat(dailyRepository.findByMerchantIdAndSettlementDate(closing, TODAY))
        .hasValueSatisfying(row -> assertThat(row.getCarryIn()).isEqualTo(-10_000L));
  }

  @Test
  @DisplayName("9번 대사는 해지가 끝난 가맹점의 다음 날 장부가 없는 것을 불일치로 보지 않는다")
  void 확정액_이월_대사에서_해지완료_제외() {
    Long closed = saveMerchantWithCarry(MerchantStatus.CLOSED);
    Long active = saveMerchantWithCarry(MerchantStatus.ACTIVE);

    // 오늘 장부를 만들지 않은 상태에서 대사한다
    var mismatches = settlementCarryCheck.check(TODAY).mismatches();

    assertThat(mismatches).doesNotContain("merchantId=" + closed);
    assertThat(mismatches).contains("merchantId=" + active);
  }

  // 어제 10,000원을 다음 날로 넘긴 가맹점을 원하는 상태로 만든다
  private Long saveMerchantWithCarry(MerchantStatus status) {
    Long merchantId =
        merchantRepository
            .save(Merchant.create(nextOwnerId++, "이월 가맹점", "인천광역시 남동구 1", true))
            .getId();
    if (status != MerchantStatus.ACTIVE) {
      merchantRepository.updateStatusIfCurrent(merchantId, MerchantStatus.ACTIVE, MerchantStatus.CLOSING);
    }
    if (status == MerchantStatus.CLOSED) {
      merchantRepository.updateStatusIfCurrent(merchantId, MerchantStatus.CLOSING, MerchantStatus.CLOSED);
    }
    dailyRepository.save(DailySettlement.create(merchantId, YESTERDAY, 0, 10_000, 0, NOW));
    return merchantId;
  }
}
