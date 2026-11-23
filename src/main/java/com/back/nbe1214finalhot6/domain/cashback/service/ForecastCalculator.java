package com.back.nbe1214finalhot6.domain.cashback.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 소진 예측의 순수 계산. 남은 예산이 하루 수요만큼 일정하게 줄어든다고 보고 소진일·공지선 도달일·일별 남은 예산을 구한다. 저장소를 읽지 않아서 같은 입력이면 항상
 * 같은 결과가 나온다.
 */
final class ForecastCalculator {

  private ForecastCalculator() {}

  /**
   * 한 시나리오의 계산 결과.
   *
   * @param exhaustionDate 소진일. 월말 안에 소진되지 않거나 수요가 0이면 null
   * @param noticeLineDate 공지선 도달일. 월말 안에 닿지 않거나 수요가 0이면 null
   * @param remainingByDay 기준일 다음 날부터 월말까지 그날 끝의 남은 예산. 소진 뒤에는 0
   */
  record Result(LocalDate exhaustionDate, LocalDate noticeLineDate, List<Long> remainingByDay) {}

  static Result calculate(
      long remaining, long contingency, BigDecimal dailyDemand, LocalDate asOf, LocalDate monthEnd) {
    LocalDate exhaustion = reachDate(remaining, 0L, dailyDemand, asOf, monthEnd);
    LocalDate noticeLine = reachDate(remaining, contingency, dailyDemand, asOf, monthEnd);
    long days = Math.max(0L, ChronoUnit.DAYS.between(asOf, monthEnd));
    List<Long> points = new ArrayList<>();
    for (long day = 1; day <= days; day++) {
      BigDecimal left = BigDecimal.valueOf(remaining).subtract(dailyDemand.multiply(BigDecimal.valueOf(day)));
      // 소진 직전까지는 1원 이상으로 남기고, 소진되는 날부터 0 이다
      points.add(left.signum() <= 0 ? 0L : left.setScale(0, RoundingMode.CEILING).longValueExact());
    }
    return new Result(exhaustion, noticeLine, points);
  }

  /** 남은 예산이 {@code threshold} 이하가 되는 날. 이미 이하면 기준일, 수요가 0이거나 월말까지 닿지 않으면 null. */
  private static LocalDate reachDate(
      long remaining, long threshold, BigDecimal dailyDemand, LocalDate asOf, LocalDate monthEnd) {
    if (remaining <= threshold) {
      return asOf;
    }
    if (dailyDemand.signum() <= 0) {
      return null;
    }
    long days =
        BigDecimal.valueOf(remaining - threshold)
            .divide(dailyDemand, 0, RoundingMode.CEILING)
            .longValueExact();
    LocalDate date = asOf.plusDays(days);
    return date.isAfter(monthEnd) ? null : date;
  }
}
