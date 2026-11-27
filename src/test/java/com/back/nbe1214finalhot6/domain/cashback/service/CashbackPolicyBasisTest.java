package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyHistoryResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.entity.PolicyBasis;
import com.back.nbe1214finalhot6.domain.cashback.entity.PolicyHistoryAction;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 요율 인하의 근거 기록. 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class CashbackPolicyBasisTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 9, 0);
  private static final long OPERATOR_ID = 99L;

  @Autowired private CashbackPolicyService policyService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackEarningRepository earningRepository;
  @Autowired private CashbackPolicyRepository policyRepository;
  @PersistenceContext private EntityManager entityManager;

  private void reduce(String rate, LocalDateTime effectiveAt, String reason) {
    policyService.reduceRate(
        new RateReductionRequest(new BigDecimal(rate), effectiveAt, reason), OPERATOR_ID);
    // 저장한 값이 DB 를 거쳐도 그대로인지 보려고 영속성 컨텍스트를 비운다
    entityManager.flush();
    entityManager.clear();
  }

  // 목적: 인하하면 사유, 변경자, 저장 시점의 이번 달 예산과 소진 예측이 새 구간의 이력에 남는지 확인한다.
  @Test
  @DisplayName("인하 시점의 예산과 예측을 이력에 남긴다")
  void recordsBudgetAndForecast() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
    // 어제 접수한 700,000원 적립: 최근 7일 하루 평균 100,000원
    earningRepository.save(
        CashbackEarning.pending(
            1L,
            7L,
            1_000_000L,
            NOW.minusDays(1),
            new BigDecimal("0.03"),
            null,
            700_000L,
            NOW.plusDays(3),
            LocalDate.of(2026, 11, 1),
            NOW));

    reduce("0.02", NOW.plusHours(3), "수요 급증");

    PolicyHistoryResponse history = policyService.findHistory(null, 0, 20).content().get(0);
    assertThat(history.reason()).isEqualTo("수요 급증");
    assertThat(history.changedBy()).isEqualTo(OPERATOR_ID);
    PolicyBasis basis = history.basis();
    assertThat(basis.previousRate()).isEqualByComparingTo("0.03");
    assertThat(basis.year()).isEqualTo(2026);
    assertThat(basis.month()).isEqualTo(11);
    assertThat(basis.allocatedAmount()).isEqualTo(1_000_000L);
    assertThat(basis.remainingAmount()).isEqualTo(1_000_000L);
    assertThat(basis.contingencyAmount()).isEqualTo(100_000L);
    assertThat(basis.dailyDemand()).isEqualByComparingTo("100000");
    assertThat(basis.midExhaustionDate()).isEqualTo(LocalDate.of(2026, 11, 28));
    assertThat(basis.noticeLineDate()).isEqualTo(LocalDate.of(2026, 11, 27));
    assertThat(basis.modelVersion()).isEqualTo("v1");
  }

  // 목적: 끝난 구간의 UPDATE 이력에는 변경자만 남고 사유와 근거는 새 구간에만 있는지 확인한다.
  @Test
  @DisplayName("사유와 근거는 새 구간의 이력에만 남는다")
  void onlyOnNewSegment() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
    // 지난달에 시작해 이번 달을 덮는 구간. 인하하면 이 구간이 끝나며 UPDATE 이력이 남는다
    policyRepository.save(
        CashbackPolicy.create(
            new BigDecimal("0.025"),
            LocalDateTime.of(2026, 10, 31, 0, 0),
            LocalDateTime.of(2026, 12, 1, 0, 0),
            LocalDateTime.of(2026, 10, 30, 9, 0)));

    reduce("0.02", NOW.plusHours(3), "수요 급증");

    assertThat(policyService.findHistory(null, 0, 20).content())
        .extracting(
            PolicyHistoryResponse::action,
            PolicyHistoryResponse::changedBy,
            PolicyHistoryResponse::reason,
            h -> h.basis() == null ? null : h.basis().previousRate().stripTrailingZeros().toPlainString())
        .containsExactly(
            tuple(PolicyHistoryAction.CREATE, OPERATOR_ID, "수요 급증", "0.025"),
            tuple(PolicyHistoryAction.UPDATE, OPERATOR_ID, null, null));
  }

  // 목적: 저장하는 달에 월 예산이 없어도 인하는 저장되고 직전 요율만 남는지 확인한다.
  @Test
  @DisplayName("이번 달 예산이 없으면 직전 요율만 남긴다")
  void withoutCurrentMonthBudget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 12, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));

    reduce("0.02", LocalDateTime.of(2026, 12, 1, 0, 0), "다음 달 인하");

    PolicyBasis basis = policyService.findHistory(null, 0, 20).content().get(0).basis();
    assertThat(basis).isEqualTo(PolicyBasis.rateOnly(basis.previousRate()));
    assertThat(basis.previousRate()).isEqualByComparingTo("0.03");
  }
}
