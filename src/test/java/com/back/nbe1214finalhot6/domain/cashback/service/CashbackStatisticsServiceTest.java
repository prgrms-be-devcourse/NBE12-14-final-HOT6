package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.dto.DailyEarningResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyLimitUsageResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 적립 통계. 테스트 시계는 2026-11-18 09:00 KST 이고 이용자 월 적립 한도는 50,000원이다. */
@IntegrationTest
@Transactional
class CashbackStatisticsServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

  @Autowired private CashbackStatisticsService statisticsService;
  @Autowired private CashbackEarningRepository earningRepository;
  @Autowired private CashbackPolicyRepository policyRepository;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private long walletSeq = 1L;
  private long earningSeq = 1L;

  private void used(String month, long amount) {
    jdbcTemplate.update(
        "insert into cashback_monthly_limit (wallet_id, limit_month, used_amount)"
            + " values (?, cast(? as date), ?)",
        walletSeq++,
        month,
        amount);
  }

  // 목적: 구간의 아래 경계는 포함하고 위 경계는 다음 구간이며, 한도를 넘은 값은 도달로, 0 과 지난달은 빠지는지 확인한다.
  @Test
  @DisplayName("한도 사용률 구간별 이용자 수를 센다")
  void countsByUsageBucket() {
    for (long amount : List.of(1L, 12_499L, 12_500L, 24_999L, 25_000L, 37_499L, 37_500L, 49_999L)) {
      used("2026-11-01", amount);
    }
    used("2026-11-01", 50_000L);
    used("2026-11-01", 60_000L);
    used("2026-11-01", 50_000L);
    used("2026-11-01", 0L);
    used("2026-10-01", 50_000L);

    MonthlyLimitUsageResponse result = statisticsService.monthlyLimitUsage();

    assertThat(result.month()).isEqualTo(11);
    assertThat(result.limit()).isEqualTo(50_000L);
    assertThat(result)
        .extracting(
            MonthlyLimitUsageResponse::below25,
            MonthlyLimitUsageResponse::below50,
            MonthlyLimitUsageResponse::below75,
            MonthlyLimitUsageResponse::below100,
            MonthlyLimitUsageResponse::reached,
            MonthlyLimitUsageResponse::userCount)
        .containsExactly(2L, 2L, 2L, 2L, 3L, 11L);
  }

  // 목적: 적립한 이용자가 없으면 모두 0 인지 확인한다.
  @Test
  @DisplayName("적립한 이용자가 없으면 0 이다")
  void emptyUsage() {
    MonthlyLimitUsageResponse result = statisticsService.monthlyLimitUsage();

    assertThat(result.userCount()).isZero();
    assertThat(result.reached()).isZero();
  }

  private void earning(LocalDateTime requestedAt, long amount) {
    long id = earningSeq++;
    earningRepository.saveAndFlush(
        CashbackEarning.pending(
            id, 1L, amount * 10, requestedAt, new BigDecimal("0.1000"), null, amount,
            requestedAt.plusDays(4), requestedAt.toLocalDate().withDayOfMonth(1), requestedAt));
  }

  // 목적: 30일의 첫날 0시는 포함하고, 평균은 그날을 뺀 직전 7일이며, 제외 건은 세지 않는지 확인한다.
  @Test
  @DisplayName("일별 적립액과 직전 7일 평균을 준다")
  void dailyEarnings() {
    LocalDate first = TODAY.minusDays(29);
    earning(first.minusDays(7).atStartOfDay(), 700L);
    earning(first.minusDays(8).atTime(23, 59), 9_999L);
    earning(first.atStartOfDay(), 100L);
    earning(TODAY.minusDays(1).atTime(23, 59), 1_000L);
    earning(TODAY.minusDays(1).atStartOfDay(), 400L);
    earning(TODAY.atTime(0, 0), 5_000L);
    earningRepository.saveAndFlush(
        CashbackEarning.excluded(
            earningSeq++, 1L, 1_000L, TODAY.atTime(1, 0),
            CashbackExclusionReason.NOT_ELIGIBLE, TODAY.atTime(1, 0)));

    List<DailyEarningResponse> days = statisticsService.dailyEarnings().days();

    assertThat(days).hasSize(30);
    assertThat(days.get(0)).isEqualTo(new DailyEarningResponse(first, 100L, 100L, null));
    assertThat(days.get(28).amount()).isEqualTo(1_400L);
    assertThat(days.get(29).amount()).isEqualTo(5_000L);
    assertThat(days.get(29).previousAverage()).isEqualTo(200L);
  }

  // 목적: 인하 구간이 있는 날은 구간 요율, 그 전은 월 기본 요율이고, 오늘 나중에 시작하는 예약은 아직 반영하지 않는지 확인한다.
  @Test
  @DisplayName("날짜별 적용 요율을 준다")
  void dailyRates() {
    monthlyBudgetRepository.saveAndFlush(
        CashbackMonthlyBudget.create(
            2026, 11, 1_000_000L, new BigDecimal("0.1"), new BigDecimal("0.1")));
    LocalDateTime past = TODAY.minusDays(30).atStartOfDay();
    policyRepository.saveAndFlush(
        CashbackPolicy.create(
            new BigDecimal("0.07"), TODAY.minusDays(3).atTime(12, 0), TODAY.atTime(23, 0), past));
    policyRepository.saveAndFlush(
        CashbackPolicy.create(new BigDecimal("0.05"), TODAY.atTime(23, 0), null, past));

    List<DailyEarningResponse> days = statisticsService.dailyEarnings().days();

    assertThat(days.get(25).rate()).isEqualByComparingTo("0.1");
    assertThat(days.get(26).rate()).isEqualByComparingTo("0.07");
    assertThat(days.get(29).rate()).isEqualByComparingTo("0.07");
  }
}
