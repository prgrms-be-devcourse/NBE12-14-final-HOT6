package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackPreviewRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackPreviewResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.service.OperatorSettingAdminService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 결제 전 미리보기. 테스트 시계는 2026-11-18 09:00 KST, 월 한도 기본 50,000원, 11월 요율 3% 다. */
@IntegrationTest
@Transactional
class CashbackPreviewTest {

  private static final long OPERATOR_ID = 99L;

  private static final long WALLET = 7L;
  private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 18, 9, 0);

  @Autowired private CashbackService service;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackBudgetStateService stateService;
  @Autowired private CashbackPolicyService policyService;
  @Autowired private CashbackMonthlyLimitService limitService;
  @Autowired private CashbackEarningRepository earningRepository;
  @Autowired private OperatorSettingAdminService settingAdminService;
  @Autowired private Clock clock;
  @PersistenceContext private EntityManager entityManager;

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  private CashbackPreviewResponse preview(boolean eligible, long walletUsed) {
    return service.preview(new CashbackPreviewRequest(WALLET, eligible, walletUsed));
  }

  private CashbackReserveResponse reserve(long paymentId, boolean eligible, long walletUsed) {
    return service.reserve(new CashbackReserveRequest(paymentId, WALLET, eligible, walletUsed, AT));
  }

  // 목적: 적립 가능한 결제의 요율·내림한 예상 적립액·남은 한도(이번 결제 반영 전)가 나오고, 직후 reserve 결과와 적립액·요율이 같은지 확인한다.
  @Test
  @DisplayName("적립 가능한 결제는 reserve 와 같은 요율·적립액을 미리 보여 준다")
  void matchesReserve() {
    CashbackPreviewResponse preview = preview(true, 12_345L);
    CashbackReserveResponse reserved = reserve(1L, true, 12_345L);

    assertThat(preview.earnable()).isTrue();
    assertThat(preview.paused()).isFalse();
    assertThat(preview.exclusionReason()).isEqualTo(CashbackExclusionReason.NONE);
    assertThat(preview.expectedAmount()).isEqualTo(370L).isEqualTo(reserved.earnedAmount());
    assertThat(preview.appliedRate()).isEqualByComparingTo(reserved.appliedRate());
    assertThat(preview.monthlyLimitRemaining()).isEqualTo(50_000L);
    assertThat(reserved.monthlyLimitRemaining()).isEqualTo(50_000L - 370L);
  }

  // 목적: 대상 아님·사용분 0·내림해서 0 이 각각 맞는 사유와 예상 0원으로 나오고, reserve 와 같은 사유인지 확인한다.
  @Test
  @DisplayName("대상 아님·사용분 0·0원 적립은 각각 맞는 사유와 0원이다")
  void excludedReasons() {
    assertThat(preview(false, 12_000L).exclusionReason())
        .isEqualTo(CashbackExclusionReason.NOT_ELIGIBLE);
    assertThat(preview(true, 0L).exclusionReason()).isEqualTo(CashbackExclusionReason.NO_WALLET_USAGE);
    CashbackPreviewResponse small = preview(true, 30L);
    assertThat(small.exclusionReason()).isEqualTo(CashbackExclusionReason.AMOUNT_TOO_SMALL);
    assertThat(small.earnable()).isFalse();
    assertThat(small.expectedAmount()).isZero();
    assertThat(small.appliedRate()).isEqualByComparingTo("0");

    assertThat(reserve(1L, false, 12_000L).exclusionReason())
        .isEqualTo(CashbackExclusionReason.NOT_ELIGIBLE);
    assertThat(reserve(2L, true, 0L).exclusionReason())
        .isEqualTo(CashbackExclusionReason.NO_WALLET_USAGE);
    assertThat(reserve(3L, true, 30L).exclusionReason())
        .isEqualTo(CashbackExclusionReason.AMOUNT_TOO_SMALL);
  }

  // 목적: 월 한도가 모자라면 남은 한도와 함께 MONTHLY_LIMIT_EXCEEDED 가 나오고, 딱 맞으면 적립 가능하며, 한도를 사용량보다 낮추면 남은 한도 0 인지(경계) 확인한다.
  @Test
  @DisplayName("월 한도가 모자라면 남은 한도와 함께 사유가 나오고 딱 맞으면 적립된다")
  void monthlyLimit() {
    settingAdminService.put(SettingKey.CASHBACK_USER_MONTHLY_LIMIT.name(), 500L);

    CashbackPreviewResponse exact = preview(true, 16_666L); // 499.98 → 499
    assertThat(exact.earnable()).isTrue();
    assertThat(exact.expectedAmount()).isEqualTo(499L);
    CashbackPreviewResponse fits = preview(true, 16_667L); // 500.01 → 500
    assertThat(fits.expectedAmount()).isEqualTo(500L);
    assertThat(fits.earnable()).isTrue();
    assertThat(exact.limitReduced()).isFalse();
    assertThat(fits.limitReduced()).isFalse();
    CashbackPreviewResponse over = preview(true, 16_700L); // 501 → 남은 한도 500 으로 줄인다
    assertThat(over.earnable()).isTrue();
    assertThat(over.limitReduced()).isTrue();
    assertThat(over.expectedAmount()).isEqualTo(500L);
    assertThat(over.monthlyLimitRemaining()).isEqualTo(500L);

    reserve(1L, true, 10_000L); // 300 사용, 남은 한도 200
    CashbackPreviewResponse reduced = preview(true, 10_000L); // 300 → 200
    assertThat(reduced.monthlyLimitRemaining()).isEqualTo(200L);
    assertThat(reduced.earnable()).isTrue();
    assertThat(reduced.limitReduced()).isTrue();
    assertThat(reduced.expectedAmount()).isEqualTo(200L);
    assertThat(reserve(2L, true, 10_000L).earnedAmount()).isEqualTo(200L); // 실제 적립도 같다

    // 한도를 다 쓰면 제외
    CashbackPreviewResponse none = preview(true, 10_000L);
    assertThat(none.earnable()).isFalse();
    assertThat(none.exclusionReason()).isEqualTo(CashbackExclusionReason.MONTHLY_LIMIT_EXCEEDED);
    assertThat(none.expectedAmount()).isZero();
    assertThat(none.limitReduced()).isFalse();
    assertThat(reserve(3L, true, 10_000L).exclusionReason())
        .isEqualTo(CashbackExclusionReason.MONTHLY_LIMIT_EXCEEDED);

    settingAdminService.put(SettingKey.CASHBACK_USER_MONTHLY_LIMIT.name(), 100L); // 사용량(300)보다 낮춤
    assertThat(preview(true, 10_000L).monthlyLimitRemaining()).isZero();
  }

  // 목적: 남은 예산이 예상 적립액보다 적거나 월 예산이 없으면 BUDGET_EXHAUSTED(paused 아님)이고, 딱 같으면 적립 가능한지(경계) 확인한다.
  @Test
  @DisplayName("남은 예산이 모자라거나 월 예산이 없으면 예산 소진이고 딱 같으면 적립된다")
  void budgetExhausted() {
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(300L, null));

    assertThat(preview(true, 10_000L).earnable()).isTrue(); // 300 == 남은 300
    CashbackPreviewResponse over = preview(true, 10_100L); // 303 > 300
    assertThat(over.earnable()).isFalse();
    assertThat(over.exclusionReason()).isEqualTo(CashbackExclusionReason.BUDGET_EXHAUSTED);
    assertThat(over.paused()).isFalse();
    assertThat(reserve(1L, true, 10_100L).exclusionReason())
        .isEqualTo(CashbackExclusionReason.BUDGET_EXHAUSTED);
  }

  // 목적: 연 예산 총액이 작아도(월 배정은 연 예산을 넘을 수 없어 월과 같은 값) 남은 금액이 모자라면 예산 소진이고 딱 같으면 적립인지 확인한다.
  @Test
  @DisplayName("연 예산이 모자라면 예산 소진이다")
  void annualExhausted() {
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(300L, null));
    budgetService.putAnnual(2026, new AnnualBudgetRequest(300L));

    assertThat(preview(true, 10_000L).earnable()).isTrue();
    assertThat(preview(true, 10_100L).exclusionReason())
        .isEqualTo(CashbackExclusionReason.BUDGET_EXHAUSTED);
  }

  // 목적: 긴급 중단(STOPPED)이면 paused 와 BUDGET_EXHAUSTED, 공지(NOTICED)면 paused 아니고 적립 가능한지 확인한다.
  @Test
  @DisplayName("중단되면 paused 와 예산 소진, 공지 상태는 적립이 계속된다")
  void pausedAndNoticed() {
    stateService.noticeStopManually(2026, 11);
    CashbackPreviewResponse noticed = preview(true, 10_000L);
    assertThat(noticed.paused()).isFalse();
    assertThat(noticed.earnable()).isTrue();

    stateService.emergencyStopManually(2026, 11);
    CashbackPreviewResponse stopped = preview(true, 10_000L);
    assertThat(stopped.paused()).isTrue();
    assertThat(stopped.earnable()).isFalse();
    assertThat(stopped.exclusionReason()).isEqualTo(CashbackExclusionReason.BUDGET_EXHAUSTED);
    assertThat(reserve(1L, true, 10_000L).exclusionReason())
        .isEqualTo(CashbackExclusionReason.BUDGET_EXHAUSTED);
  }

  // 목적: 달 중 인하 구간이 시작되면 그 요율로 계산하는지 확인한다. 시계를 인하 시각 뒤로 옮겨 확인한다.
  @Test
  @DisplayName("달 중 인하 구간이 시작되면 그 요율로 계산한다")
  void rateReduction() {
    policyService.reduceRate(
        new RateReductionRequest(new BigDecimal("0.02"), LocalDateTime.of(2026, 11, 18, 12, 0), "테스트 사유"), OPERATOR_ID);
    assertThat(preview(true, 10_000L).expectedAmount()).isEqualTo(300L);

    ((MutableClock) clock).advance(Duration.ofHours(4)); // 13:00

    CashbackPreviewResponse after = preview(true, 10_000L);
    assertThat(after.expectedAmount()).isEqualTo(200L);
    assertThat(after.appliedRate()).isEqualByComparingTo("0.02");
  }

  // 목적: 미리보기를 여러 번 불러도 월 한도 사용량·예산 확보·적립 건이 바뀌지 않는지 확인한다.
  @Test
  @DisplayName("미리보기는 한도·예산·적립 건을 바꾸지 않는다")
  void readOnly() {
    preview(true, 12_000L);
    preview(true, 12_000L);
    preview(false, 12_000L);

    entityManager.flush();
    entityManager.clear();
    assertThat(limitService.usedAmount(WALLET, YearMonth.of(2026, 11))).isZero();
    assertThat(budgetService.getMonthly(2026, 11).reservedAmount()).isZero();
    assertThat(budgetService.getAnnual(2026).reservedAmount()).isZero();
    assertThat(earningRepository.count()).isZero();
  }
}
