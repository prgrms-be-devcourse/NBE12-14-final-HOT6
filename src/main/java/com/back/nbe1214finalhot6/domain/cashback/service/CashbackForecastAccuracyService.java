package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastAccuracyDay;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastAccuracyResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackForecastSnapshot;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackForecastSnapshotRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.DailyAmount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 하루 한 번 소진 예측을 저장하고, 지난 날의 예측을 실제 적립 확보액과 비교한다. */
@Service
@RequiredArgsConstructor
public class CashbackForecastAccuracyService {

  static final int COMPARE_DAYS = 30;

  private final CashbackForecastService forecastService;
  private final CashbackForecastSnapshotRepository snapshotRepository;
  private final CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  private final CashbackEarningRepository earningRepository;
  private final Clock clock;

  /**
   * 오늘의 예측을 저장한다. 이미 저장했거나 이번 달 예산이 없으면 아무것도 하지 않는다. 지난 날은 채우지 않는다.
   *
   * @return 저장했으면 true
   */
  @Transactional
  public boolean recordToday() {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDate today = now.toLocalDate();
    if (snapshotRepository.existsBySnapshotDate(today)
        || monthlyBudgetRepository
            .findByBudgetYearAndBudgetMonth(today.getYear(), today.getMonthValue())
            .isEmpty()) {
      return false;
    }
    snapshotRepository.save(CashbackForecastSnapshot.of(forecastService.forecast(null, null), now));
    return true;
  }

  /** 오늘을 뺀 최근 {@value #COMPARE_DAYS}일 중 예측을 저장한 날의 오차. 실제 값은 조회할 때 계산하므로 뒤에 취소된 적립은 빠진다. */
  @Transactional(readOnly = true)
  public ForecastAccuracyResponse accuracy() {
    LocalDate today = LocalDate.now(clock);
    LocalDate from = today.minusDays(COMPARE_DAYS);
    // 실제 값은 기간 전체를 한 번에 읽어 날짜별로 나눈다
    Map<LocalDate, Long> actualByDay =
        earningRepository
            .sumAmountByRequestedDay(
                from.atStartOfDay(), today.atStartOfDay(), CashbackForecastService.DEMAND_STATUSES)
            .stream()
            .collect(Collectors.toMap(DailyAmount::getDay, DailyAmount::getAmount));
    List<ForecastAccuracyDay> days =
        snapshotRepository
            .findBySnapshotDateGreaterThanEqualAndSnapshotDateLessThanOrderBySnapshotDateDesc(
                from, today)
            .stream()
            .map(s -> compare(s, actualByDay.getOrDefault(s.getSnapshotDate(), 0L)))
            .toList();
    Long averageError =
        days.isEmpty()
            ? null
            : Math.round(days.stream().mapToLong(d -> Math.abs(d.difference())).average().orElse(0));
    int within = (int) days.stream().filter(ForecastAccuracyDay::withinRange).count();
    return new ForecastAccuracyResponse(averageError, within, days);
  }

  private static ForecastAccuracyDay compare(CashbackForecastSnapshot s, long actual) {
    BigDecimal actualValue = BigDecimal.valueOf(actual);
    return new ForecastAccuracyDay(
        s.getSnapshotDate(),
        s.getMidDailyDemand(),
        s.getSlowDailyDemand(),
        s.getFastDailyDemand(),
        actual,
        actualValue.subtract(s.getMidDailyDemand()).setScale(0, RoundingMode.HALF_UP).longValue(),
        actualValue.compareTo(s.getSlowDailyDemand()) >= 0
            && actualValue.compareTo(s.getFastDailyDemand()) <= 0,
        s.getModelVersion());
  }
}
