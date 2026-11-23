package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetMonthSeries;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetTimePoint;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetTransferRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예산 일별 시계열. 11월·12월 배정 각 1,000,000, 기본 요율 3% 라서 12,000원 사용분은 360원, 10,000원은 300원이 적립된다. 테스트 시계는 시작이 2026-11-18
 * 09:00 KST 이고 날짜마다 옮겨 가며 지급·취소 시각을 만든다.
 */
@IntegrationTest
@Transactional
class CashbackBudgetTimeseriesServiceTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final AtomicLong USER_SEQ = new AtomicLong(System.currentTimeMillis());

  @Autowired private CashbackBudgetTimeseriesService timeseriesService;
  @Autowired private CashbackService cashbackService;
  @Autowired private CashbackPayoutService payoutService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackBudgetTransferService transferService;
  @Autowired private WalletRepository walletRepository;
  @Autowired private Clock clock;
  @PersistenceContext private EntityManager entityManager;

  private Long walletId;

  @BeforeEach
  void setUp() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
    budgetService.putMonthly(2026, 12, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
    walletId = walletRepository.save(Wallet.create(USER_SEQ.incrementAndGet(), LocalDateTime.now(clock))).getId();
  }

  private void at(LocalDate day) {
    ((MutableClock) clock).set(day.atTime(9, 0).atZone(KST).toInstant());
  }

  /** 10,000원 사용분 -> 300원 적립 예정. 지급 예정은 접수 다음 날 + 3일 00:00 이다. */
  private void reserve(long paymentId, LocalDate requestedDay, boolean eligible) {
    cashbackService.reserve(
        new CashbackReserveRequest(paymentId, walletId, eligible, 10_000L, requestedDay.atTime(10, 0)));
  }

  private BudgetMonthSeries only(List<BudgetMonthSeries> list) {
    assertThat(list).hasSize(1);
    return list.get(0);
  }

  private BudgetTimePoint point(BudgetMonthSeries series, LocalDate day) {
    return series.points().stream().filter(p -> p.date().equals(day)).findFirst().orElseThrow();
  }

  private MonthlyBudgetResponse monthly(int year, int month) {
    entityManager.flush();
    entityManager.clear();
    return budgetService.getMonthly(year, month);
  }

  // 목적: 접수한 날짜에 확보가 누적되고 변동 없는 날은 직전 값을 이어 가며, 남은 예산이 월 배정에서 확보를 뺀 값인지, 시리즈가 월 시작부터 오늘까지 한 달치만 나오는지
  // (12월 예산은 아직 시작 전) 확인한다.
  @Test
  @DisplayName("접수한 날부터 확보가 누적되고 변동 없는 날은 직전 값을 이어 간다")
  void reservedAccumulates() {
    reserve(1L, LocalDate.of(2026, 11, 3), true);
    reserve(2L, LocalDate.of(2026, 11, 3), true);
    reserve(3L, LocalDate.of(2026, 11, 10), true);

    BudgetMonthSeries series =
        only(timeseriesService.getTimeseries(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 18)));

    assertThat(series.year()).isEqualTo(2026);
    assertThat(series.month()).isEqualTo(11);
    assertThat(series.allocatedAmount()).isEqualTo(1_000_000L);
    assertThat(series.contingencyAmount()).isEqualTo(100_000L);
    assertThat(series.points()).hasSize(18);
    assertThat(point(series, LocalDate.of(2026, 11, 2)).reservedAmount()).isZero();
    BudgetTimePoint nov3 = point(series, LocalDate.of(2026, 11, 3));
    assertThat(nov3.reservedAmount()).isEqualTo(600L);
    assertThat(nov3.remainingAmount()).isEqualTo(999_400L);
    assertThat(point(series, LocalDate.of(2026, 11, 9)).reservedAmount()).isEqualTo(600L);
    assertThat(point(series, LocalDate.of(2026, 11, 10)).reservedAmount()).isEqualTo(900L);
    assertThat(point(series, LocalDate.of(2026, 11, 18)).paidAmount()).isZero();
  }

  // 목적: 지급되면 그날부터 지급이 늘고 확보가 같은 만큼 줄며 남은 예산은 변하지 않는지 확인한다.
  @Test
  @DisplayName("지급하면 확보가 지급으로 옮겨지고 남은 예산은 그대로다")
  void paidMovesFromReserved() {
    reserve(1L, LocalDate.of(2026, 11, 3), true);
    at(LocalDate.of(2026, 11, 8));
    payoutService.run();
    at(LocalDate.of(2026, 11, 18));

    BudgetMonthSeries series =
        only(timeseriesService.getTimeseries(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 18)));

    BudgetTimePoint before = point(series, LocalDate.of(2026, 11, 7));
    BudgetTimePoint after = point(series, LocalDate.of(2026, 11, 8));
    assertThat(before.reservedAmount()).isEqualTo(300L);
    assertThat(before.paidAmount()).isZero();
    assertThat(after.reservedAmount()).isZero();
    assertThat(after.paidAmount()).isEqualTo(300L);
    assertThat(after.remainingAmount()).isEqualTo(before.remainingAmount());
  }

  // 목적: 지급 전 취소는 취소한 날부터 확보를 줄여 남은 예산을 되돌리고, 지급 후 회수는 지급도 확보도 줄이지 않는지(예산을 되돌리지 않음) 확인한다.
  @Test
  @DisplayName("지급 전 취소는 남은 예산을 되돌리고 지급 후 회수는 예산에 영향이 없다")
  void cancelAndClawBack() {
    reserve(1L, LocalDate.of(2026, 11, 3), true);
    reserve(2L, LocalDate.of(2026, 11, 3), true);
    at(LocalDate.of(2026, 11, 6));
    cashbackService.cancel(1L); // 지급 전 취소
    at(LocalDate.of(2026, 11, 8));
    payoutService.run(); // 2번 지급
    at(LocalDate.of(2026, 11, 12));
    cashbackService.cancel(2L); // 지급 후 회수
    at(LocalDate.of(2026, 11, 18));

    BudgetMonthSeries series =
        only(timeseriesService.getTimeseries(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 18)));

    assertThat(point(series, LocalDate.of(2026, 11, 5)).reservedAmount()).isEqualTo(600L);
    BudgetTimePoint canceled = point(series, LocalDate.of(2026, 11, 6));
    assertThat(canceled.reservedAmount()).isEqualTo(300L);
    assertThat(canceled.remainingAmount()).isEqualTo(999_700L);
    BudgetTimePoint paid = point(series, LocalDate.of(2026, 11, 8));
    assertThat(paid.paidAmount()).isEqualTo(300L);
    assertThat(paid.reservedAmount()).isZero();
    BudgetTimePoint clawed = point(series, LocalDate.of(2026, 11, 12));
    assertThat(clawed.paidAmount()).isEqualTo(300L);
    assertThat(clawed.reservedAmount()).isZero();
    assertThat(clawed.remainingAmount()).isEqualTo(999_700L);
  }

  // 목적: 마지막 점의 세 값이 월 예산 조회의 완료·확보·남음과 같은지 확인한다(지급·취소·이월이 섞인 상태).
  @Test
  @DisplayName("마지막 점의 값이 월 예산 조회와 같다")
  void lastPointMatchesBudget() {
    reserve(1L, LocalDate.of(2026, 11, 3), true);
    reserve(2L, LocalDate.of(2026, 11, 3), true);
    reserve(3L, LocalDate.of(2026, 11, 14), true);
    at(LocalDate.of(2026, 11, 6));
    cashbackService.cancel(1L);
    at(LocalDate.of(2026, 11, 8));
    payoutService.run();
    at(LocalDate.of(2026, 11, 15));
    transferService.transfer(new BudgetTransferRequest(2026, 11, 200_000L));
    at(LocalDate.of(2026, 11, 18));

    BudgetMonthSeries series =
        only(timeseriesService.getTimeseries(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 18)));

    BudgetTimePoint last = series.points().get(series.points().size() - 1);
    MonthlyBudgetResponse budget = monthly(2026, 11);
    assertThat(last.paidAmount()).isEqualTo(budget.paidAmount());
    assertThat(last.reservedAmount()).isEqualTo(budget.reservedAmount());
    assertThat(last.remainingAmount()).isEqualTo(budget.remainingAmount());
  }

  // 목적: 이월한 날부터 남은 예산이 바뀌고(넘긴 달은 줄고), 월 시작 전에 넘어온 이월은 받는 달의 첫 점부터 반영되는지 확인한다.
  @Test
  @DisplayName("이월한 날부터 남은 예산이 바뀌고 받는 달은 첫 점부터 반영한다")
  void carriedReflectedByDate() {
    at(LocalDate.of(2026, 11, 15));
    transferService.transfer(new BudgetTransferRequest(2026, 11, 200_000L));
    at(LocalDate.of(2026, 12, 3));

    List<BudgetMonthSeries> list =
        timeseriesService.getTimeseries(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 12, 2));

    BudgetMonthSeries november = list.get(0);
    BudgetMonthSeries december = list.get(1);
    assertThat(point(november, LocalDate.of(2026, 11, 14)).remainingAmount()).isEqualTo(1_000_000L);
    assertThat(point(november, LocalDate.of(2026, 11, 15)).remainingAmount()).isEqualTo(800_000L);
    assertThat(december.points().get(0).date()).isEqualTo(LocalDate.of(2026, 12, 1));
    assertThat(december.points().get(0).remainingAmount()).isEqualTo(1_200_000L);
  }

  // 목적: 월 말 이후에 지급된 건이 그 달 시리즈의 해당 날짜에 이어서 나오고, 같은 기간의 다음 달 시리즈와 함께 달별로 나뉘며, 시리즈가 마지막 변동 날짜에서 끝나는지
  // 확인한다.
  @Test
  @DisplayName("월 말 이후의 지급은 그 달 시리즈에 이어지고 달별로 나뉜다")
  void paidAfterMonthEnd() {
    reserve(1L, LocalDate.of(2026, 11, 29), true); // 지급 예정 12-03 00:00
    at(LocalDate.of(2026, 12, 4));
    payoutService.run();
    at(LocalDate.of(2026, 12, 10));

    List<BudgetMonthSeries> list =
        timeseriesService.getTimeseries(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 8));

    assertThat(list).hasSize(2);
    BudgetMonthSeries november = list.get(0);
    BudgetMonthSeries december = list.get(1);
    assertThat(november.points().get(0).date()).isEqualTo(LocalDate.of(2026, 11, 28));
    assertThat(november.points().get(november.points().size() - 1).date())
        .isEqualTo(LocalDate.of(2026, 12, 4));
    assertThat(point(november, LocalDate.of(2026, 12, 3)).reservedAmount()).isEqualTo(300L);
    BudgetTimePoint paid = point(november, LocalDate.of(2026, 12, 4));
    assertThat(paid.paidAmount()).isEqualTo(300L);
    assertThat(paid.reservedAmount()).isZero();
    assertThat(december.points().get(0).date()).isEqualTo(LocalDate.of(2026, 12, 1));
    assertThat(december.points().get(december.points().size() - 1).date())
        .isEqualTo(LocalDate.of(2026, 12, 8));
  }

  // 목적: 점의 값이 조회 기간과 무관한지(월 시작부터 누적), 제외 건은 확보에 들어가지 않는지 확인한다.
  @Test
  @DisplayName("값은 조회 기간과 무관하고 제외 건은 확보에 들어가지 않는다")
  void valuesIndependentOfRange() {
    reserve(1L, LocalDate.of(2026, 11, 3), true);
    reserve(2L, LocalDate.of(2026, 11, 4), false); // 적립 대상 아님 -> 제외

    BudgetMonthSeries wide =
        only(timeseriesService.getTimeseries(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 18)));
    BudgetMonthSeries narrow =
        only(timeseriesService.getTimeseries(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)));

    assertThat(narrow.points()).hasSize(3);
    assertThat(point(narrow, LocalDate.of(2026, 11, 10)))
        .isEqualTo(point(wide, LocalDate.of(2026, 11, 10)));
    assertThat(point(narrow, LocalDate.of(2026, 11, 10)).reservedAmount()).isEqualTo(300L);
  }

  // 목적: 미래 날짜는 오늘로 잘리고, 오늘 이후만 조회하거나 겹치는 월 예산이 없으면 빈 목록인지 확인한다.
  @Test
  @DisplayName("미래는 오늘까지만 나오고 겹치는 월 예산이 없으면 빈 목록이다")
  void clippedAndEmpty() {
    BudgetMonthSeries clipped =
        only(timeseriesService.getTimeseries(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 12, 31)));

    assertThat(clipped.points()).extracting(BudgetTimePoint::date)
        .containsExactly(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 18));
    assertThat(timeseriesService.getTimeseries(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 12, 31))).isEmpty();
    assertThat(timeseriesService.getTimeseries(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))).isEmpty();
  }

  // 목적: 시작이 끝보다 늦거나 366일을 넘는 기간은 CSH-007 로 거절되고, 딱 366일은 허용되는지(경계) 확인한다.
  @Test
  @DisplayName("잘못된 기간은 거절하고 366일까지는 허용한다")
  void invalidRange() {
    assertThatThrownBy(() -> timeseriesService.getTimeseries(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 17)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
    assertThatThrownBy(() -> timeseriesService.getTimeseries(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
    assertThat(timeseriesService.getTimeseries(LocalDate.of(2025, 11, 18), LocalDate.of(2026, 11, 18))).isNotNull();
  }
}
