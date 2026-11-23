package com.back.nbe1214finalhot6.domain.settlement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MonthlySettlementTest {

  private static final LocalDate OCTOBER = LocalDate.of(2026, 10, 1);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 0, 30);

  @Test
  @DisplayName("만들면 지급 대기 상태로 시작하고 지급 시각은 비어 있다")
  void 생성시_대기상태() {
    MonthlySettlement settlement = MonthlySettlement.create(1L, OCTOBER, 15_000L, NOW);

    assertThat(settlement.getStatus()).isEqualTo(MonthlySettlementStatus.PENDING);
    assertThat(settlement.getPayoutAmount()).isEqualTo(15_000L);
    assertThat(settlement.getPaidAt()).isNull();
    assertThat(settlement.isPaid()).isFalse();
  }

  @Test
  @DisplayName("지급액이 0원 이하이면 만들 수 없다")
  void 지급액_0원이하_거절() {
    assertThatThrownBy(() -> MonthlySettlement.create(1L, OCTOBER, 0L, NOW))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
  }

  @Test
  @DisplayName("정산월이 1일이 아니면 만들 수 없다")
  void 정산월_1일아님_거절() {
    assertThatThrownBy(() -> MonthlySettlement.create(1L, LocalDate.of(2026, 10, 15), 1_000L, NOW))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("지급 완료로 바꾸면 상태와 지급 시각이 기록된다")
  void 지급완료_처리() {
    MonthlySettlement settlement = MonthlySettlement.create(1L, OCTOBER, 15_000L, NOW);
    LocalDateTime paidAt = NOW.plusMinutes(1);

    settlement.markPaid(paidAt);

    assertThat(settlement.isPaid()).isTrue();
    assertThat(settlement.getPaidAt()).isEqualTo(paidAt);
  }
}
