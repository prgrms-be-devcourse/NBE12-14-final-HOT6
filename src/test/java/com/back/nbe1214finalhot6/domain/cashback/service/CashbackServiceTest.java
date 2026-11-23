package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.service.OperatorSettingAdminService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 적립 확보. 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class CashbackServiceTest {

  private static final long WALLET = 7L;
  private static final YearMonth NOV = YearMonth.of(2026, 11);
  private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 18, 9, 0);

  @Autowired private CashbackService service;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackPolicyService policyService;
  @Autowired private CashbackMonthlyLimitService limitService;
  @Autowired private CashbackEarningRepository earningRepository;
  @Autowired private OperatorSettingAdminService settingAdminService;
  @PersistenceContext private EntityManager entityManager;

  // 연 예산 12,000,000 / 11월 배정 1,000,000, 기본 요율 3%
  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  private CashbackReserveRequest request(
      long paymentId, boolean eligible, long walletUsed, LocalDateTime at) {
    return new CashbackReserveRequest(paymentId, WALLET, eligible, walletUsed, at);
  }

  private long monthlyReserved() {
    entityManager.flush();
    entityManager.clear();
    return budgetService.getMonthly(2026, 11).reservedAmount();
  }

  private long annualReserved() {
    entityManager.flush();
    entityManager.clear();
    return budgetService.getAnnual(2026).reservedAmount();
  }

  // 목적: 정상 적립이 요율·내림·지급 예정 시각·남은 한도를 맞게 돌려주고, 한도 사용량·예산 확보·적립 건 저장까지 함께 일어나는지 확인한다.
  @Test
  @DisplayName("적립 대상이면 적립액을 확보하고 적립 예정 건을 남긴다")
  void reserve_earns() {
    CashbackReserveResponse result = service.reserve(request(1L, true, 12_000L, AT));

    assertThat(result.earnedAmount()).isEqualTo(360L);
    assertThat(result.appliedRate()).isEqualByComparingTo("0.03");
    assertThat(result.appliedPolicyId()).isNull();
    // 결제 다음날 00:00 + 지급 지연 3일
    assertThat(result.payableAt()).isEqualTo(LocalDateTime.of(2026, 11, 22, 0, 0));
    assertThat(result.exclusionReason()).isEqualTo(CashbackExclusionReason.NONE);
    assertThat(result.monthlyLimitRemaining()).isEqualTo(50_000L - 360L);

    assertThat(limitService.usedAmount(WALLET, NOV)).isEqualTo(360L);
    assertThat(monthlyReserved()).isEqualTo(360L);
    assertThat(annualReserved()).isEqualTo(360L);
    CashbackEarning earning = earningRepository.findByPaymentId(1L).orElseThrow();
    assertThat(earning.getStatus()).isEqualTo(EarningStatus.PENDING);
    assertThat(earning.getAmount()).isEqualTo(360L);
    assertThat(earning.getLimitMonth()).isEqualTo(NOV.atDay(1));
  }

  // 목적: 소수점 이하를 버리는지(올림·반올림이 아닌지) 확인한다. 12,345 × 3% = 370.35.
  @Test
  @DisplayName("적립액은 소수점 이하를 버린다")
  void reserve_floors() {
    CashbackReserveResponse result = service.reserve(request(1L, true, 12_345L, AT));

    assertThat(result.earnedAmount()).isEqualTo(370L);
  }

  // 목적: 제외 사유 세 가지(대상 아님, 사용분 0, 내림해서 0)가 맞게 나오고 한도·예산은 건드리지 않으며 제외 건이 남는지 확인한다.
  @Test
  @DisplayName("적립 대상이 아니거나 사용분이 없거나 0원이 되면 제외 건을 남기고 확보하지 않는다")
  void reserve_excludesBeforeReserving() {
    assertThat(service.reserve(request(1L, false, 12_000L, AT)).exclusionReason())
        .isEqualTo(CashbackExclusionReason.NOT_ELIGIBLE);
    assertThat(service.reserve(request(2L, true, 0L, AT)).exclusionReason())
        .isEqualTo(CashbackExclusionReason.NO_WALLET_USAGE);
    // 30 × 3% = 0.9 → 0
    CashbackReserveResponse small = service.reserve(request(3L, true, 30L, AT));
    assertThat(small.exclusionReason()).isEqualTo(CashbackExclusionReason.AMOUNT_TOO_SMALL);
    assertThat(small.earnedAmount()).isZero();
    assertThat(small.payableAt()).isNull();
    assertThat(small.monthlyLimitRemaining()).isEqualTo(50_000L);

    assertThat(limitService.usedAmount(WALLET, NOV)).isZero();
    assertThat(monthlyReserved()).isZero();
    CashbackEarning earning = earningRepository.findByPaymentId(3L).orElseThrow();
    assertThat(earning.getStatus()).isEqualTo(EarningStatus.EXCLUDED);
    assertThat(earning.getExclusionReason()).isEqualTo(CashbackExclusionReason.AMOUNT_TOO_SMALL);
    assertThat(earning.getAmount()).isZero();
  }

  // 목적: 사유가 여러 개 겹칠 때 정해 둔 순서(대상 아님 > 사용분 없음)로 하나만 기록되는지 확인한다.
  @Test
  @DisplayName("제외 사유가 겹치면 먼저 걸리는 하나만 쓴다")
  void reserve_reasonPriority() {
    CashbackReserveResponse result = service.reserve(request(1L, false, 0L, AT));

    assertThat(result.exclusionReason()).isEqualTo(CashbackExclusionReason.NOT_ELIGIBLE);
  }

  // 목적: 월 한도를 넘는 적립은 전액 제외되고(부분 적립 없음) 한도·예산이 그대로인지 확인한다. 1,700,000 × 3% = 51,000 > 50,000.
  @Test
  @DisplayName("월 한도를 넘으면 적립하지 않고 아무것도 확보하지 않는다")
  void reserve_monthlyLimitExceeded() {
    CashbackReserveResponse result = service.reserve(request(1L, true, 1_700_000L, AT));

    assertThat(result.earnedAmount()).isZero();
    assertThat(result.exclusionReason()).isEqualTo(CashbackExclusionReason.MONTHLY_LIMIT_EXCEEDED);
    assertThat(result.monthlyLimitRemaining()).isEqualTo(50_000L);
    assertThat(limitService.usedAmount(WALLET, NOV)).isZero();
    assertThat(monthlyReserved()).isZero();
  }

  // 목적: 한도가 운영자 설정에서 오는지, 딱 한도까지는 적립되고 그 뒤는 제외되는지(경계) 확인한다.
  @Test
  @DisplayName("월 한도는 운영자 설정값을 쓰고 한도까지는 적립한다")
  void reserve_usesOperatorLimit() {
    settingAdminService.put(SettingKey.CASHBACK_USER_MONTHLY_LIMIT.name(), 1_000L);

    // 한도 1,000원: 900 적립 후 남은 100원보다 큰 102 는 제외, 99 는 적립
    assertThat(service.reserve(request(1L, true, 30_000L, AT)).earnedAmount()).isEqualTo(900L);
    CashbackReserveResponse exact = service.reserve(request(2L, true, 3_400L, AT));
    assertThat(exact.earnedAmount()).isZero();
    assertThat(exact.exclusionReason()).isEqualTo(CashbackExclusionReason.MONTHLY_LIMIT_EXCEEDED);
    assertThat(exact.monthlyLimitRemaining()).isEqualTo(100L);
    assertThat(service.reserve(request(3L, true, 3_333L, AT)).earnedAmount()).isEqualTo(99L);
    assertThat(limitService.usedAmount(WALLET, NOV)).isEqualTo(999L);
  }

  // 목적: 예산이 모자라면 먼저 차감한 월 한도를 되돌리고 BUDGET_EXHAUSTED 로 제외되는지 확인한다.
  @Test
  @DisplayName("예산 확보가 실패하면 차감한 월 한도를 되돌리고 제외한다")
  void reserve_budgetExhausted_restoresLimit() {
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(100L, null));

    CashbackReserveResponse result = service.reserve(request(1L, true, 12_000L, AT));

    assertThat(result.earnedAmount()).isZero();
    assertThat(result.exclusionReason()).isEqualTo(CashbackExclusionReason.BUDGET_EXHAUSTED);
    assertThat(result.monthlyLimitRemaining()).isEqualTo(50_000L);
    assertThat(limitService.usedAmount(WALLET, NOV)).isZero();
    assertThat(annualReserved()).isZero();
    assertThat(earningRepository.findByPaymentId(1L).orElseThrow().getStatus())
        .isEqualTo(EarningStatus.EXCLUDED);
  }

  // 목적: 월 예산이 없는 달(요율도 없음)에 접수된 결제는 예산 소진으로 제외되고 아무것도 확보하지 않는지 확인한다.
  @Test
  @DisplayName("월 예산이 없는 달은 예산 소진으로 제외한다")
  void reserve_noMonthlyBudget() {
    CashbackReserveResponse result =
        service.reserve(request(1L, true, 12_000L, LocalDateTime.of(2026, 10, 31, 23, 0)));

    assertThat(result.exclusionReason()).isEqualTo(CashbackExclusionReason.BUDGET_EXHAUSTED);
    assertThat(limitService.usedAmount(WALLET, YearMonth.of(2026, 10))).isZero();
  }

  // 목적: 접수 시각에 유효한 요율을 쓰는지(인하 전 시각은 기본 요율, 이후는 인하 구간·정책 ID) 확인한다.
  @Test
  @DisplayName("요율은 접수 시각 기준이고 인하 구간이면 정책 ID 를 함께 돌려준다")
  void reserve_usesRateAtRequestTime() {
    var reduced =
        policyService.reduceRate(
            new RateReductionRequest(new BigDecimal("0.02"), LocalDateTime.of(2026, 11, 18, 12, 0)));

    CashbackReserveResponse before =
        service.reserve(request(1L, true, 10_000L, LocalDateTime.of(2026, 11, 18, 10, 0)));
    CashbackReserveResponse after =
        service.reserve(request(2L, true, 10_000L, LocalDateTime.of(2026, 11, 18, 13, 0)));

    assertThat(before.earnedAmount()).isEqualTo(300L);
    assertThat(before.appliedPolicyId()).isNull();
    assertThat(after.earnedAmount()).isEqualTo(200L);
    assertThat(after.appliedRate()).isEqualByComparingTo("0.02");
    assertThat(after.appliedPolicyId()).isEqualTo(reduced.policyId());
  }

  // 목적: 같은 결제로 다시 호출해도 한도·예산을 한 번만 쓰고 처음 결과를 돌려주는지 확인한다(제외 건도 같다).
  @Test
  @DisplayName("같은 결제로 다시 호출하면 처음 결과를 돌려주고 다시 확보하지 않는다")
  void reserve_isIdempotent() {
    CashbackReserveResponse first = service.reserve(request(1L, true, 12_000L, AT));
    CashbackReserveResponse again = service.reserve(request(1L, true, 12_000L, AT));
    CashbackReserveResponse excluded1 = service.reserve(request(2L, false, 12_000L, AT));
    CashbackReserveResponse excluded2 = service.reserve(request(2L, false, 12_000L, AT));

    assertThat(again).isEqualTo(first);
    assertThat(excluded2).isEqualTo(excluded1);
    assertThat(limitService.usedAmount(WALLET, NOV)).isEqualTo(360L);
    assertThat(monthlyReserved()).isEqualTo(360L);
    assertThat(earningRepository.count()).isEqualTo(2);
  }

  // 목적: 한도를 이미 쓴 양보다 낮춘 경우 남은 한도가 음수가 아니라 0 으로 나오는지 확인한다.
  @Test
  @DisplayName("한도를 사용량보다 낮추면 남은 한도는 0 이다")
  void reserve_remainingNeverNegative() {
    service.reserve(request(1L, true, 30_000L, AT));
    settingAdminService.put(SettingKey.CASHBACK_USER_MONTHLY_LIMIT.name(), 500L);

    CashbackReserveResponse result = service.reserve(request(2L, true, 30_000L, AT));

    assertThat(result.exclusionReason()).isEqualTo(CashbackExclusionReason.MONTHLY_LIMIT_EXCEEDED);
    assertThat(result.monthlyLimitRemaining()).isZero();
  }

  @Test
  @DisplayName("취소는 아직 아무 일도 하지 않고 여러 번 호출해도 예외가 없다")
  void cancel_doesNothingYet() {
    assertThatCode(
            () -> {
              service.cancel(1L);
              service.cancel(1L);
            })
        .doesNotThrowAnyException();
  }
}
