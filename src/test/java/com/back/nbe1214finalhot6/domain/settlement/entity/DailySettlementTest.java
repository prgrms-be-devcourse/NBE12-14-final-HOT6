package com.back.nbe1214finalhot6.domain.settlement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailySettlementTest {

  private static final LocalDate DATE = LocalDate.of(2026, 11, 17);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 0, 10);

  @Test
  @DisplayName("순매출이 양수이고 이월이 없으면 순매출이 그대로 확정된다")
  void 양수순매출_그대로확정() {
    DailySettlement settlement = DailySettlement.create(1L, DATE, 15_000L, 5_000L, 0L, NOW);

    assertThat(settlement.getNetSales()).isEqualTo(10_000L);
    assertThat(settlement.getConfirmedAmount()).isEqualTo(10_000L);
    assertThat(settlement.getCarryOut()).isZero();
  }

  @Test
  @DisplayName("순매출이 음수이면 0원으로 정산하고 부족분을 이월한다")
  void 음수순매출_이월() {
    DailySettlement settlement = DailySettlement.create(1L, DATE, 0L, 30_000L, 0L, NOW);

    assertThat(settlement.getNetSales()).isEqualTo(-30_000L);
    assertThat(settlement.getConfirmedAmount()).isZero();
    assertThat(settlement.getCarryOut()).isEqualTo(-30_000L);
  }

  @Test
  @DisplayName("전일 이월이 있으면 양수 매출에서 먼저 차감하고 남은 금액만 확정한다")
  void 이월_상계() {
    DailySettlement settlement = DailySettlement.create(1L, DATE, 50_000L, 0L, -30_000L, NOW);

    assertThat(settlement.getConfirmedAmount()).isEqualTo(20_000L);
    assertThat(settlement.getCarryOut()).isZero();
  }

  @Test
  @DisplayName("양수 매출이 이월 부족분보다 작으면 확정액은 0원이고 부족분이 줄어든 채 이월된다")
  void 이월_일부만상계() {
    DailySettlement settlement = DailySettlement.create(1L, DATE, 10_000L, 0L, -30_000L, NOW);

    assertThat(settlement.getConfirmedAmount()).isZero();
    assertThat(settlement.getCarryOut()).isEqualTo(-20_000L);
  }

  @Test
  @DisplayName("확정액과 다음 날 이월의 합은 순매출과 전일 이월의 합과 같다")
  void 확정액_이월_등식() {
    DailySettlement settlement = DailySettlement.create(1L, DATE, 12_000L, 40_000L, -5_000L, NOW);

    assertThat(settlement.getConfirmedAmount() + settlement.getCarryOut())
        .isEqualTo(settlement.getNetSales() + settlement.getCarryIn());
  }

  @Test
  @DisplayName("결제 합과 취소 합이 음수이면 생성할 수 없다")
  void 음수합계_거절() {
    assertThatThrownBy(() -> DailySettlement.create(1L, DATE, -1L, 0L, 0L, NOW))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
  }

  @Test
  @DisplayName("전일 이월이 양수이면 생성할 수 없다")
  void 양수이월_거절() {
    assertThatThrownBy(() -> DailySettlement.create(1L, DATE, 0L, 0L, 1L, NOW))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
  }
}
