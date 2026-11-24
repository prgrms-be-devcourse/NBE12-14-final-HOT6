package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 소진 예측 순수 계산. 기준일 2026-11-18, 월말 11-30(남은 날 12일)이다. */
class ForecastCalculatorTest {

  private static final LocalDate AS_OF = LocalDate.of(2026, 11, 18);
  private static final LocalDate END = LocalDate.of(2026, 11, 30);

  private ForecastCalculator.Result calc(long remaining, long contingency, String daily) {
    return ForecastCalculator.calculate(remaining, contingency, new BigDecimal(daily), AS_OF, END);
  }

  // 목적: 소진일(올림 일수)·공지선 도달일·일별 남은 예산이 맞고, 소진된 뒤에는 0 으로 이어지며 점이 기준일 다음 날부터 월말까지 12개인지 확인한다.
  @Test
  @DisplayName("소진일과 공지선 도달일과 예상 선을 계산한다")
  void basic() {
    ForecastCalculator.Result result = calc(1_000_000L, 100_000L, "100000.00");

    assertThat(result.exhaustionDate()).isEqualTo(LocalDate.of(2026, 11, 28));
    assertThat(result.noticeLineDate()).isEqualTo(LocalDate.of(2026, 11, 27));
    assertThat(result.remainingByDay()).hasSize(12);
    assertThat(result.remainingByDay().get(0)).isEqualTo(900_000L);
    assertThat(result.remainingByDay().get(8)).isEqualTo(100_000L); // 9일째: 공지선에 닿음
    assertThat(result.remainingByDay().get(9)).isZero(); // 10일째: 소진
    assertThat(result.remainingByDay().get(11)).isZero(); // 소진 이후에도 0
  }

  // 목적: 남은 예산이 하루 수요로 딱 나누어떨어지면 그 날 소진이고, 1원이라도 남으면 다음 날 소진인지(올림 경계) 확인한다.
  @Test
  @DisplayName("딱 나누어떨어지면 그날 소진이고 1원이라도 남으면 다음 날이다")
  void exhaustionBoundary() {
    assertThat(calc(1_000_000L, 0L, "100000.00").exhaustionDate()).isEqualTo(LocalDate.of(2026, 11, 28));
    assertThat(calc(1_000_001L, 0L, "100000.00").exhaustionDate()).isEqualTo(LocalDate.of(2026, 11, 29));
  }

  // 목적: 소진일이 월말 당일이면 값이 있고, 월말 다음 날이면 이번 달 안에 소진되지 않으므로 null 인지(월 경계) 확인한다.
  @Test
  @DisplayName("월말 당일 소진은 날짜가 있고 다음 날부터는 없음이다")
  void monthEndBoundary() {
    assertThat(calc(1_200_000L, 0L, "100000.00").exhaustionDate()).isEqualTo(END);
    assertThat(calc(1_300_000L, 0L, "100000.00").exhaustionDate()).isNull();
    assertThat(calc(1_000_000L, 0L, "10000.00").exhaustionDate()).isNull();
  }

  // 목적: 수요가 0 이면 소진일·공지선 도달일이 없고 예상 선이 남은 예산 그대로 이어지는지 확인한다.
  @Test
  @DisplayName("수요가 0 이면 소진일이 없고 예상 선은 그대로다")
  void zeroDemand() {
    ForecastCalculator.Result result = calc(1_000_000L, 100_000L, "0.00");

    assertThat(result.exhaustionDate()).isNull();
    assertThat(result.noticeLineDate()).isNull();
    assertThat(result.remainingByDay()).hasSize(12).containsOnly(1_000_000L);
  }

  // 목적: 이미 소진됐거나 공지선 아래이면 도달일이 기준일이고(수요가 0 이어도), 예상 선은 0 으로 이어지는지 확인한다.
  @Test
  @DisplayName("이미 소진됐거나 공지선 아래이면 도달일이 기준일이다")
  void alreadyReached() {
    ForecastCalculator.Result exhausted = calc(0L, 100_000L, "0.00");
    assertThat(exhausted.exhaustionDate()).isEqualTo(AS_OF);
    assertThat(exhausted.noticeLineDate()).isEqualTo(AS_OF);
    assertThat(exhausted.remainingByDay()).containsOnly(0L);

    ForecastCalculator.Result belowNotice = calc(80_000L, 100_000L, "10000.00");
    assertThat(belowNotice.noticeLineDate()).isEqualTo(AS_OF);
    assertThat(belowNotice.exhaustionDate()).isEqualTo(LocalDate.of(2026, 11, 26));
  }

  // 목적: 기준일이 월말이면 예상 선이 비고(월말을 넘지 않음), 소수 수요에서도 소진 직전 점이 1원 이상으로 남는지 확인한다.
  @Test
  @DisplayName("월말이 기준일이면 예상 선이 비고 소수 수요도 소진 직전까지 양수다")
  void lastDayAndFractionalDemand() {
    ForecastCalculator.Result lastDay =
        ForecastCalculator.calculate(500L, 0L, new BigDecimal("100.00"), END, END);
    assertThat(lastDay.remainingByDay()).isEmpty();
    assertThat(lastDay.exhaustionDate()).isNull();

    ForecastCalculator.Result fractional = calc(1_000L, 0L, "333.33"); // 3일째 999.99 → 소진은 4일째
    assertThat(fractional.remainingByDay().get(2)).isEqualTo(1L);
    assertThat(fractional.remainingByDay().get(3)).isZero();
    assertThat(fractional.exhaustionDate()).isEqualTo(LocalDate.of(2026, 11, 22));
  }
}
