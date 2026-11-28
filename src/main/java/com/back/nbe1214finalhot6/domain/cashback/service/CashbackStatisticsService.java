package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.dto.DailyEarningResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.DailyEarningTrendResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyLimitUsageResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyLimitRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.DailyAmount;
import com.back.nbe1214finalhot6.domain.cashback.repository.LimitUsageCount;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.service.OperatorSettingService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자의 적립 통계. 새로 저장하지 않고 월 한도 사용량, 적립 건, 요율 기록을 읽어 센다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CashbackStatisticsService {

  static final int TREND_DAYS = 30;
  private static final int AVERAGE_DAYS = 7;

  private final CashbackMonthlyLimitRepository limitRepository;
  private final CashbackEarningRepository earningRepository;
  private final CashbackPolicyRepository policyRepository;
  private final CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  private final OperatorSettingService settingService;
  private final Clock clock;

  /** 이번 달에 적립한 이용자의 한도 사용률 분포. 기준은 지금 적용 중인 한도다. */
  public MonthlyLimitUsageResponse monthlyLimitUsage() {
    LocalDate today = LocalDate.now(clock);
    long limit = settingService.getLong(SettingKey.CASHBACK_USER_MONTHLY_LIMIT);
    LimitUsageCount c = limitRepository.countByUsage(today.withDayOfMonth(1), limit);
    long total =
        c.getBelow25() + c.getBelow50() + c.getBelow75() + c.getBelow100() + c.getReached();
    return new MonthlyLimitUsageResponse(
        today.getYear(),
        today.getMonthValue(),
        limit,
        total,
        c.getBelow25(),
        c.getBelow50(),
        c.getBelow75(),
        c.getBelow100(),
        c.getReached());
  }

  /** 오늘까지 {@value #TREND_DAYS}일의 날짜별 신규 적립액, 직전 7일 평균, 적용 요율. 세는 상태는 소진 예측과 같다. */
  public DailyEarningTrendResponse dailyEarnings() {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDate today = now.toLocalDate();
    LocalDate first = today.minusDays(TREND_DAYS - 1L);
    Map<LocalDate, Long> amounts =
        earningRepository
            .sumAmountByRequestedDay(
                first.minusDays(AVERAGE_DAYS).atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                CashbackForecastService.DEMAND_STATUSES)
            .stream()
            .collect(Collectors.toMap(DailyAmount::getDay, DailyAmount::getAmount));
    List<CashbackPolicy> policies = policyRepository.findAllByOrderByStartsAtDesc();
    Map<YearMonth, Optional<BigDecimal>> baseRates = new HashMap<>();
    Function<YearMonth, Optional<BigDecimal>> baseRate =
        month ->
            monthlyBudgetRepository
                .findByBudgetYearAndBudgetMonth(month.getYear(), month.getMonthValue())
                .map(CashbackMonthlyBudget::getBaseRate);
    return new DailyEarningTrendResponse(
        first
            .datesUntil(today.plusDays(1))
            .map(
                day -> {
                  LocalDateTime endOfDay = day.atTime(LocalTime.MAX);
                  LocalDateTime at = endOfDay.isAfter(now) ? now : endOfDay;
                  BigDecimal rate =
                      effectivePolicy(policies, at)
                          .map(CashbackPolicy::getRate)
                          .or(() -> baseRates.computeIfAbsent(YearMonth.from(day), baseRate))
                          .orElse(null);
                  return new DailyEarningResponse(
                      day, amounts.getOrDefault(day, 0L), previousAverage(amounts, day), rate);
                })
            .toList());
  }

  // 시작이 늦은 순으로 받은 구간에서 그 시각에 유효한 것
  private static Optional<CashbackPolicy> effectivePolicy(
      List<CashbackPolicy> policies, LocalDateTime at) {
    return policies.stream()
        .filter(p -> !p.getStartsAt().isAfter(at))
        .filter(p -> p.getEndsAt() == null || p.getEndsAt().isAfter(at))
        .findFirst();
  }

  private static long previousAverage(Map<LocalDate, Long> amounts, LocalDate day) {
    long sum =
        LongStream.rangeClosed(1, AVERAGE_DAYS)
            .map(i -> amounts.getOrDefault(day.minusDays(i), 0L))
            .sum();
    return Math.round((double) sum / AVERAGE_DAYS);
  }
}
