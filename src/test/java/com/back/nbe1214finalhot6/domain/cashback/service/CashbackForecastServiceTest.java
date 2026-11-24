package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소진 예측. 테스트 시계는 2026-11-18 09:00 KST 이고 11월 배정 1,000,000(대비금 100,000), 수요 배율 1.0, 범위 폭 0.3, 이용자 월 한도 50,000 이다. 수요
 * 기간은 11-11 ~ 11-17 이다.
 */
@IntegrationTest
@Transactional
class CashbackForecastServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

  @Autowired private CashbackForecastService forecastService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackEarningRepository earningRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @PersistenceContext private EntityManager entityManager;

  private long paymentSeq = 1L;

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
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

  private void setStatus(long paymentId, String status) {
    entityManager.flush();
    jdbcTemplate.update("update cashback_earning set status = ? where payment_id = ?", status, paymentId);
    entityManager.clear();
  }

  /** 11-11 ~ 11-17 에 하루 한 건씩 100,000원: 일평균 100,000. */
  private void steadyDemand() {
    for (int day = 11; day <= 17; day++) {
      earning(LocalDateTime.of(2026, 11, day, 12, 0), 100_000L);
    }
  }

  // 목적: 최근 7일 일평균을 수요로 빠름·중앙·느림의 소진일·공지선 도달일, 예상 선(기준일 다음 날부터 월말), 최소 보장 인원, 가정·버전이 맞게 나오는지 확인한다.
  @Test
  @DisplayName("최근 7일 일평균으로 세 시나리오와 가정을 계산한다")
  void forecastFromRecentDemand() {
    steadyDemand();

    ForecastResponse result = forecastService.forecast(null, null);

    assertThat(result.year()).isEqualTo(2026);
    assertThat(result.month()).isEqualTo(11);
    assertThat(result.remainingAmount()).isEqualTo(1_000_000L);
    assertThat(result.contingencyAmount()).isEqualTo(100_000L);
    assertThat(result.guaranteedUsers()).isEqualTo(20L); // 1,000,000 / 50,000
    assertThat(result.assumptions().demandBasis()).isEqualTo("RECENT_7D_AVG");
    assertThat(result.assumptions().baseDailyDemand()).isEqualByComparingTo("100000");
    assertThat(result.assumptions().demandMultiplier()).isEqualByComparingTo("1.0");
    assertThat(result.assumptions().rangeRatio()).isEqualByComparingTo("0.3");
    assertThat(result.assumptions().modelVersion()).isEqualTo("v1");
    assertThat(result.assumptions().asOf()).isEqualTo(TODAY);

    assertThat(result.mid().dailyDemand()).isEqualByComparingTo("100000");
    assertThat(result.fast().dailyDemand()).isEqualByComparingTo("130000");
    assertThat(result.slow().dailyDemand()).isEqualByComparingTo("70000");
    assertThat(result.fast().exhaustionDate()).isEqualTo(LocalDate.of(2026, 11, 26)); // ceil(7.69) = 8일
    assertThat(result.mid().exhaustionDate()).isEqualTo(LocalDate.of(2026, 11, 28)); // 10일
    assertThat(result.slow().exhaustionDate()).isNull(); // ceil(14.29) = 15일 → 12월
    assertThat(result.fast().noticeLineDate()).isEqualTo(LocalDate.of(2026, 11, 25));
    assertThat(result.mid().noticeLineDate()).isEqualTo(LocalDate.of(2026, 11, 27));
    assertThat(result.slow().noticeLineDate()).isNull();
    assertThat(result.fast().exhaustionDate()).isBeforeOrEqualTo(result.mid().exhaustionDate());

    assertThat(result.points()).hasSize(12);
    assertThat(result.points().get(0).date()).isEqualTo(LocalDate.of(2026, 11, 19));
    assertThat(result.points().get(11).date()).isEqualTo(LocalDate.of(2026, 11, 30));
    assertThat(result.points().get(0).midRemaining()).isEqualTo(900_000L);
    assertThat(result.points().get(6).fastRemaining()).isEqualTo(90_000L); // 7일째
    assertThat(result.points().get(7).fastRemaining()).isZero(); // 8일째 소진
    assertThat(result.points().get(11).slowRemaining()).isEqualTo(160_000L); // 12일째
  }

  // 목적: 수요 기간(기준일 제외 최근 7일)의 앞뒤 경계, 취소 건 제외, 지급 후 회수 건 포함을 확인한다.
  @Test
  @DisplayName("수요 기간은 기준일을 빼고 취소는 제외하며 회수는 포함한다")
  void demandWindowAndStatuses() {
    steadyDemand(); // 700,000
    earning(LocalDateTime.of(2026, 11, 10, 23, 59), 1_000_000L); // 7일 전이라 제외
    earning(LocalDateTime.of(2026, 11, 18, 0, 0), 1_000_000L); // 오늘이라 제외
    long canceled = earning(LocalDateTime.of(2026, 11, 15, 10, 0), 1_000_000L);
    setStatus(canceled, "CANCELED"); // 취소라 제외
    long clawed = earning(LocalDateTime.of(2026, 11, 16, 10, 0), 70_000L);
    setStatus(clawed, "CLAWED_BACK"); // 회수는 예산을 되돌리지 않았으므로 포함

    ForecastResponse result = forecastService.forecast(2026, 11);

    // (700,000 + 70,000) / 7 = 110,000
    assertThat(result.assumptions().baseDailyDemand()).isEqualByComparingTo("110000.00");
  }

  // 목적: 최근 수요가 없으면 소진일이 없고 예상 선이 남은 예산 그대로인지, 호출이 한도·예산·적립 건을 바꾸지 않는지 확인한다.
  @Test
  @DisplayName("수요가 없으면 소진일이 없고 호출은 아무것도 바꾸지 않는다")
  void noDemandAndReadOnly() {
    earning(LocalDateTime.of(2026, 11, 14, 10, 0), 100L);
    long earnings = earningRepository.count();

    ForecastResponse result = forecastService.forecast(null, null);
    forecastService.forecast(null, null);

    assertThat(result.assumptions().baseDailyDemand()).isEqualByComparingTo("14.29");
    assertThat(result.points().get(11).midRemaining()).isGreaterThan(990_000L);
    entityManager.flush();
    entityManager.clear();
    assertThat(budgetService.getMonthly(2026, 11).reservedAmount()).isZero();
    assertThat(earningRepository.count()).isEqualTo(earnings);

    ForecastResponse empty = forecastService.forecast(2026, 11);
    assertThat(empty).isEqualTo(forecastService.forecast(2026, 11)); // 같은 입력이면 같은 결과
  }

  // 목적: 이미 소진된 달은 도달일이 기준일이고 예상 선이 0 인지 확인한다.
  @Test
  @DisplayName("이미 소진된 달은 도달일이 기준일이다")
  void exhaustedMonth() {
    budgetService.reserveBudget(2026, 11, 1_000_000L); // 월 배정을 모두 확보해 남은 예산을 0 으로 만든다
    entityManager.flush();
    entityManager.clear(); // 조건부 UPDATE 는 읽어 둔 엔티티를 갱신하지 않는다

    ForecastResponse result = forecastService.forecast(null, null);

    assertThat(result.remainingAmount()).isZero();
    assertThat(result.mid().exhaustionDate()).isEqualTo(TODAY);
    assertThat(result.mid().noticeLineDate()).isEqualTo(TODAY);
    assertThat(result.guaranteedUsers()).isZero();
    assertThat(result.points()).allMatch(p -> p.midRemaining() == 0L);
  }

  // 목적: 이번 달이 아닌 월·하나만 준 파라미터·잘못된 월은 CSH-007 이고, 이번 달 월 예산이 없으면 CSH-004 인지 확인한다.
  @Test
  @DisplayName("이번 달이 아니거나 파라미터가 잘못되면 거절하고 월 예산이 없으면 404 다")
  void invalidRequests() {
    assertThatThrownBy(() -> forecastService.forecast(2026, 12))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
    assertThatThrownBy(() -> forecastService.forecast(2026, null))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
    assertThatThrownBy(() -> forecastService.forecast(2026, 13))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);

    jdbcTemplate.update("delete from cashback_monthly_budget");
    entityManager.clear();
    assertThatThrownBy(() -> forecastService.forecast(null, null))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.BUDGET_NOT_FOUND);
  }
}
