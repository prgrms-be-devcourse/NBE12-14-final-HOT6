package com.back.nbe1214finalhot6.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementResponse;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlementStatus;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class MonthlySettlementQueryServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 0, 30);

  @Autowired MonthlySettlementQueryService queryService;
  @Autowired MonthlySettlementRepository settlementRepository;
  @Autowired MerchantRepository merchantRepository;

  private Long merchantId;
  private Long otherMerchantId;

  @BeforeEach
  void setUp() {
    merchantId = merchantRepository.save(Merchant.create(8_001L, "내 가게", "서울", true)).getId();
    otherMerchantId =
        merchantRepository.save(Merchant.create(8_002L, "남의 가게", "서울", true)).getId();
  }

  @Test
  @DisplayName("기간 안의 내 가맹점 월 정산만 정산월 순으로 반환한다")
  void 기간안_내가맹점만() {
    save(merchantId, YearMonth.of(2026, 8), 5_000L, false);
    save(merchantId, YearMonth.of(2026, 10), 15_000L, false);
    save(merchantId, YearMonth.of(2026, 9), 10_000L, true);
    save(otherMerchantId, YearMonth.of(2026, 9), 99_000L, true);

    List<MonthlySettlementResponse> result =
        queryService.getMonthly(merchantId, YearMonth.of(2026, 9), YearMonth.of(2026, 10));

    assertThat(result)
        .extracting(MonthlySettlementResponse::settlementMonth)
        .containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10));
    assertThat(result.get(0).payoutAmount()).isEqualTo(10_000L);
    assertThat(result.get(0).status()).isEqualTo(MonthlySettlementStatus.PAID);
    assertThat(result.get(0).paidAt()).isEqualTo(NOW);
    assertThat(result.get(1).status()).isEqualTo(MonthlySettlementStatus.PENDING);
    assertThat(result.get(1).paidAt()).isNull();
  }

  @Test
  @DisplayName("12개월까지는 조회할 수 있다")
  void 열두달_허용() {
    save(merchantId, YearMonth.of(2025, 11), 5_000L, true);

    List<MonthlySettlementResponse> result =
        queryService.getMonthly(merchantId, YearMonth.of(2025, 11), YearMonth.of(2026, 10));

    assertThat(result).hasSize(1);
  }

  @Test
  @DisplayName("13개월 이상이거나 시작이 끝보다 늦으면 STL-004 로 거절한다")
  void 기간오류() {
    assertPeriodRejected(YearMonth.of(2025, 10), YearMonth.of(2026, 10));
    assertPeriodRejected(YearMonth.of(2026, 10), YearMonth.of(2026, 9));
  }

  @Test
  @DisplayName("없는 가맹점이면 MER-001 로 거절한다")
  void 가맹점없음() {
    assertThatThrownBy(
            () ->
                queryService.getMonthly(
                    Long.MAX_VALUE, YearMonth.of(2026, 9), YearMonth.of(2026, 10)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_NOT_FOUND));
  }

  private void assertPeriodRejected(YearMonth from, YearMonth to) {
    assertThatThrownBy(() -> queryService.getMonthly(merchantId, from, to))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(SettlementErrorCode.INVALID_MONTH_PERIOD));
  }

  private void save(Long merchant, YearMonth month, long amount, boolean paid) {
    MonthlySettlement settlement = MonthlySettlement.create(merchant, month.atDay(1), amount, NOW);
    if (paid) {
      settlement.markPaid(NOW);
    }
    settlementRepository.save(settlement);
  }
}
