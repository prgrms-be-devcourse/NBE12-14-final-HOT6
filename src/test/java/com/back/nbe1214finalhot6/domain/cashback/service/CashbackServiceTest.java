package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CashbackServiceTest {

  private final CashbackService service = new CashbackService();

  @Test
  @DisplayName("임시 구현은 적립 대상이어도 항상 적립 0원을 돌려준다")
  void 항상0원() {
    CashbackReserveRequest command =
        new CashbackReserveRequest(1L, 1L, true, 12_000L, LocalDateTime.of(2026, 11, 18, 9, 0));

    CashbackReserveResponse result = service.reserve(command);

    assertThat(result.earnedAmount()).isZero();
    assertThat(result.exclusionReason()).isEqualTo(CashbackExclusionReason.NOT_ELIGIBLE);
  }

  @Test
  @DisplayName("취소는 아무 일도 하지 않고 여러 번 호출해도 예외가 없다")
  void 취소는아무일도하지않는다() {
    assertThatCode(
            () -> {
              service.cancel(1L);
              service.cancel(1L);
            })
        .doesNotThrowAnyException();
  }
}
