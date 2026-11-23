package com.back.nbe1214finalhot6.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.settlement.dto.DailySettlementResponse;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class DailySettlementQueryServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 0, 10);

  @Autowired DailySettlementQueryService queryService;
  @Autowired DailySettlementRepository settlementRepository;
  @Autowired MerchantRepository merchantRepository;

  @AfterEach
  void cleanUp() {
    settlementRepository.deleteAll();
    merchantRepository.deleteAll();
  }

  @Test
  @DisplayName("기간 안의 정산을 날짜 오름차순으로 돌려주고 기간 밖은 제외한다")
  void 기간조회_날짜순() {
    Long merchantId = saveMerchant();
    saveSettlement(merchantId, LocalDate.of(2026, 11, 17));
    saveSettlement(merchantId, LocalDate.of(2026, 11, 15));
    saveSettlement(merchantId, LocalDate.of(2026, 11, 10));

    List<DailySettlementResponse> result =
        queryService.getDaily(merchantId, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 17));

    assertThat(result)
        .extracting(DailySettlementResponse::settlementDate)
        .containsExactly(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17));
  }

  @Test
  @DisplayName("다른 가맹점의 정산은 조회되지 않는다")
  void 다른가맹점_제외() {
    Long merchantId = saveMerchant();
    Long otherId = saveMerchant();
    saveSettlement(otherId, LocalDate.of(2026, 11, 17));

    List<DailySettlementResponse> result =
        queryService.getDaily(merchantId, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("없는 가맹점이면 MER-001 로 거절한다")
  void 가맹점없음() {
    assertError(
        () -> queryService.getDaily(999L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)),
        MerchantErrorCode.MERCHANT_NOT_FOUND);
  }

  @Test
  @DisplayName("시작일이 종료일보다 늦으면 STL-002 로 거절한다")
  void 기간역전() {
    Long merchantId = saveMerchant();

    assertError(
        () -> queryService.getDaily(merchantId, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 4)),
        SettlementErrorCode.INVALID_PERIOD);
  }

  @Test
  @DisplayName("31일까지는 조회되고 32일째부터는 STL-002 로 거절한다")
  void 기간상한() {
    Long merchantId = saveMerchant();
    LocalDate from = LocalDate.of(2026, 11, 1);

    assertThat(queryService.getDaily(merchantId, from, from.plusDays(30))).isEmpty();
    assertError(
        () -> queryService.getDaily(merchantId, from, from.plusDays(31)),
        SettlementErrorCode.INVALID_PERIOD);
  }

  private void assertError(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }

  // 다른 테스트가 쓰는 작은 점주 번호(1 등)와 겹치지 않게 큰 값에서 시작한다
  private long nextOwnerId = 8_000L;

  private Long saveMerchant() {
    // 점주 한 명당 가맹점 하나이므로 가맹점마다 점주 번호를 달리한다
    return merchantRepository.save(Merchant.create(nextOwnerId++, "테스트 가맹점", "서울", true)).getId();
  }

  private void saveSettlement(Long merchantId, LocalDate date) {
    settlementRepository.save(DailySettlement.create(merchantId, date, 1_000L, 0L, 0L, NOW));
  }
}
