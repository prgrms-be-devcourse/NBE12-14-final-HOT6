package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastAccuracyDay;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastAccuracyResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackForecastSnapshot;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackForecastSnapshotRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 소진 예측 저장과 오차. 테스트 시계는 2026-11-18 09:00 KST, 수요 배율 1.0, 범위 폭 0.3 이다. */
@IntegrationTest
@Transactional
class CashbackForecastAccuracyTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

  @Autowired private CashbackForecastAccuracyService accuracyService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackForecastSnapshotRepository snapshotRepository;
  @Autowired private CashbackEarningRepository earningRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MutableClock clock;
  @PersistenceContext private EntityManager entityManager;

  private long paymentSeq = 1L;

  private void budget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(
        2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  private long earning(LocalDateTime requestedAt, long amount) {
    long paymentId = paymentSeq++;
    earningRepository.save(
        CashbackEarning.pending(
            paymentId,
            7L,
            amount * 30,
            requestedAt,
            new BigDecimal("0.03"),
            null,
            amount,
            requestedAt.toLocalDate().plusDays(4).atStartOfDay(),
            LocalDate.of(2026, 11, 1),
            requestedAt));
    return paymentId;
  }

  /** 11-11 ~ 11-17 에 하루 100,000원: 11-18 의 중간 수요 100,000, 범위 70,000 ~ 130,000. */
  private void steadyDemand() {
    for (int day = 11; day <= 17; day++) {
      earning(LocalDateTime.of(2026, 11, day, 12, 0), 100_000L);
    }
  }

  // 목적: 그날의 예측이 한 번만 저장되고 날짜가 바뀌면 새로 저장되는지 확인한다.
  @Test
  @DisplayName("예측은 하루 한 건만 저장한다")
  void recordsOncePerDay() {
    budget();
    steadyDemand();

    assertThat(accuracyService.recordToday()).isTrue();
    assertThat(accuracyService.recordToday()).isFalse();

    CashbackForecastSnapshot saved = snapshotRepository.findAll().get(0);
    assertThat(saved.getSnapshotDate()).isEqualTo(TODAY);
    assertThat(saved.getRemainingAmount()).isEqualTo(1_000_000L);
    assertThat(saved.getContingencyAmount()).isEqualTo(100_000L);
    assertThat(saved.getMidDailyDemand()).isEqualByComparingTo("100000");
    assertThat(saved.getFastDailyDemand()).isEqualByComparingTo("130000");
    assertThat(saved.getSlowDailyDemand()).isEqualByComparingTo("70000");
    assertThat(saved.getMidExhaustionDate()).isEqualTo(LocalDate.of(2026, 11, 28));
    assertThat(saved.getNoticeLineDate()).isEqualTo(LocalDate.of(2026, 11, 27));
    assertThat(saved.getModelVersion()).isEqualTo("v1");
    clock.advance(Duration.ofDays(1));
    assertThat(accuracyService.recordToday()).isTrue();
    assertThat(snapshotRepository.count()).isEqualTo(2);
  }

  // 목적: 이번 달 예산이 없으면 오류 없이 저장하지 않는지 확인한다.
  @Test
  @DisplayName("이번 달 예산이 없으면 저장하지 않는다")
  void skipsWithoutBudget() {
    assertThat(accuracyService.recordToday()).isFalse();
    assertThat(snapshotRepository.count()).isZero();
  }

  // 목적: 지난 날의 예상과 실제의 차이·범위·평균이 맞고 오늘은 빠지며, 취소한 적립은 실제에서 빠지는지 확인한다.
  @Test
  @DisplayName("지난 날의 예측을 실제 확보액과 비교한다")
  void comparesPastDays() {
    budget();
    steadyDemand();
    accuracyService.recordToday(); // 11-18
    earning(LocalDateTime.of(2026, 11, 18, 0, 0), 120_000L);
    long canceled = earning(LocalDateTime.of(2026, 11, 18, 23, 59), 50_000L);
    clock.advance(Duration.ofDays(1));
    accuracyService.recordToday(); // 11-19: 아무 결제도 없는 날
    assertThat(accuracyService.accuracy().days()).hasSize(1);
    clock.advance(Duration.ofDays(1));
    entityManager.flush();
    jdbcTemplate.update(
        "update cashback_earning set status = 'CANCELED' where payment_id = ?", canceled);
    entityManager.clear();

    ForecastAccuracyResponse result = accuracyService.accuracy();

    assertThat(result.days())
        .extracting(ForecastAccuracyDay::date)
        .containsExactly(TODAY.plusDays(1), TODAY);
    ForecastAccuracyDay first = result.days().get(1);
    assertThat(first.actualAmount()).isEqualTo(120_000L);
    assertThat(first.difference()).isEqualTo(20_000L);
    assertThat(first.withinRange()).isTrue();
    ForecastAccuracyDay second = result.days().get(0);
    assertThat(second.actualAmount()).isZero();
    assertThat(second.withinRange()).isFalse();
    assertThat(second.difference()).isNegative();
    assertThat(result.withinRangeDays()).isEqualTo(1);
    assertThat(result.averageError()).isEqualTo(Math.round((20_000L - second.difference()) / 2.0));
  }

  // 목적: 비교할 날이 없으면 평균이 null 이고, 30일보다 오래된 예측은 빠지는지 확인한다.
  @Test
  @DisplayName("비교할 날이 없으면 평균이 없다")
  void emptyAndOutOfRange() {
    budget();
    accuracyService.recordToday();
    assertThat(accuracyService.accuracy().averageError()).isNull();

    clock.advance(Duration.ofDays(30));
    assertThat(accuracyService.accuracy().days()).hasSize(1);
    clock.advance(Duration.ofDays(1));
    assertThat(accuracyService.accuracy().days()).isEmpty();
  }
}
