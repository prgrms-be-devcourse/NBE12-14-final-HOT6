package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicyHistory;
import com.back.nbe1214finalhot6.domain.cashback.entity.PolicyHistoryAction;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyHistoryRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/** 기본 설정(예고 0일, 한 달 인하 1회)에서의 요율 인하와 조회. 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class CashbackPolicyServiceTest {

  private static final long OPERATOR_ID = 99L;

  private static final LocalDateTime NOV_20 = LocalDateTime.of(2026, 11, 20, 0, 0);
  private static final LocalDateTime DEC_1 = LocalDateTime.of(2026, 12, 1, 0, 0);
  private static final LocalDateTime DEC_10 = LocalDateTime.of(2026, 12, 10, 0, 0);
  private static final LocalDateTime JAN_1 = LocalDateTime.of(2027, 1, 1, 0, 0);

  @Autowired private CashbackPolicyService policyService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackPolicyRepository policyRepository;
  @Autowired private CashbackPolicyHistoryRepository historyRepository;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;

  // 11월 예산은 기본 요율 0.05 로 둔다
  @BeforeEach
  void setUpNovemberBudget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(
        2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.05")));
  }

  private PolicyResponse reduce(String rate, LocalDateTime effectiveAt) {
    return policyService.reduceRate(new RateReductionRequest(new BigDecimal(rate), effectiveAt, "테스트 사유"), OPERATOR_ID);
  }

  private void assertReduceError(String rate, LocalDateTime effectiveAt, CashbackErrorCode code) {
    assertThatThrownBy(() -> reduce(rate, effectiveAt))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", code);
  }

  // 목적: 인하하면 적용 시각부터 월 말(다음 달 1일 0시, 미포함)까지의 구간이 생기고, 경계 시각에서 요율이 정확히 바뀌는지 확인한다.
  @Test
  @DisplayName("인하하면 적용 시각부터 월 말까지 새 요율의 구간이 생긴다")
  void reduce_createsSegmentUntilMonthEnd() {
    PolicyResponse created = reduce("0.03", NOV_20);

    assertThat(created.rate().toPlainString()).isEqualTo("0.0300");
    assertThat(created.startsAt()).isEqualTo(NOV_20);
    assertThat(created.endsAt()).isEqualTo(DEC_1);
    assertThat(policyService.effectiveRate(NOV_20.minusSeconds(1)))
        .contains(new BigDecimal("0.0500"));
    assertThat(policyService.effectiveRate(NOV_20)).contains(new BigDecimal("0.0300"));
    assertThat(policyService.effectiveRate(DEC_1.minusSeconds(1)))
        .contains(new BigDecimal("0.0300"));
    // 12월 예산이 없으면 다음 달에는 적용할 요율이 없다
    assertThat(policyService.effectiveRate(DEC_1)).isEmpty();
  }

  // 목적: 새 요율이 현재 요율과 같거나 높으면(인상 포함) CSH-003 으로 거부되고 저장되지 않는지 확인한다.
  @Test
  @DisplayName("현재 요율과 같거나 높은 요율은 거부한다")
  void reduce_rejectsNotLower() {
    assertReduceError("0.05", NOV_20, CashbackErrorCode.INVALID_POLICY);
    assertReduceError("0.06", NOV_20, CashbackErrorCode.INVALID_POLICY);

    assertThat(policyRepository.count()).isZero();
    assertThat(historyRepository.count()).isZero();
  }

  // 목적: 요율 형식 오류가 CSH-003 으로 거부되는지 확인한다.
  @Test
  @DisplayName("요율 형식이 올바르지 않으면 거부한다")
  void reduce_rejectsInvalidRate() {
    assertReduceError("0", NOV_20, CashbackErrorCode.INVALID_POLICY);
    assertReduceError("0.00001", NOV_20, CashbackErrorCode.INVALID_POLICY);
    assertReduceError("-0.01", NOV_20, CashbackErrorCode.INVALID_POLICY);
  }

  // 목적: 적용 시각이 현재 분보다 과거이면 거부되고, 현재 분이면 허용되는지 확인한다(예고 0일).
  @Test
  @DisplayName("적용 시각은 현재 분 이후여야 한다")
  void reduce_effectiveAtNotBeforeNow() {
    assertReduceError(
        "0.03", LocalDateTime.of(2026, 11, 18, 8, 59), CashbackErrorCode.INVALID_POLICY);

    assertThat(reduce("0.03", LocalDateTime.of(2026, 11, 18, 9, 0)).startsAt())
        .isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 0));
  }

  // 목적: 적용 시각이 속한 달의 월 예산이 없으면 CSH-004 로 거부되는지 확인한다.
  @Test
  @DisplayName("그 달의 월 예산이 없으면 CSH-004")
  void reduce_requiresMonthlyBudget() {
    assertReduceError("0.03", DEC_10, CashbackErrorCode.BUDGET_NOT_FOUND);
  }

  // 목적: 월 기본 요율이 없는 이전 행(기본 요율 도입 전)은 인하 기준이 없어 CSH-003 으로 거부되는지 확인한다.
  @Test
  @DisplayName("월 기본 요율이 비어 있으면 인하할 수 없다")
  void reduce_requiresBaseRate() {
    CashbackMonthlyBudget stored =
        monthlyBudgetRepository.findByBudgetYearAndBudgetMonth(2026, 11).orElseThrow();
    ReflectionTestUtils.setField(stored, "baseRate", null);

    assertReduceError("0.03", NOV_20, CashbackErrorCode.INVALID_POLICY);
  }

  // 목적: 한 달 1회 설정에서 두 번째 인하가 CSH-009 로 거부되고, 첫 인하 결과는 그대로인지 확인한다.
  @Test
  @DisplayName("한 달 인하 횟수를 넘으면 CSH-009 이고 기존 구간은 그대로다")
  void reduce_limitExceeded() {
    reduce("0.04", NOV_20);

    assertReduceError(
        "0.03",
        LocalDateTime.of(2026, 11, 25, 0, 0),
        CashbackErrorCode.RATE_REDUCTION_LIMIT_EXCEEDED);

    List<CashbackPolicy> all = policyRepository.findAll();
    assertThat(all).hasSize(1);
    assertThat(all.getFirst().getEndsAt()).isEqualTo(DEC_1);
  }

  // 목적: 인하 횟수가 달마다 따로 세어져, 11월에 인하했어도 12월에 다시 인하할 수 있는지 확인한다.
  @Test
  @DisplayName("인하 횟수는 달마다 센다")
  void reduce_limitIsPerMonth() {
    reduce("0.04", NOV_20);
    // 12월 기본 요율은 11월의 기본 요율(0.05)을 이어받는다. 11월의 인하 구간(0.04)이 아니다
    budgetService.putMonthly(2026, 12, new MonthlyBudgetRequest(1_000_000L, null));
    assertThat(policyService.effectiveRate(DEC_10)).contains(new BigDecimal("0.0500"));

    PolicyResponse dec = reduce("0.03", DEC_10);

    assertThat(dec.endsAt()).isEqualTo(JAN_1);
    assertThat(policyService.effectiveRate(DEC_10)).contains(new BigDecimal("0.0300"));
  }

  // 목적: 인하 한 번에 구간 생성 이력 1건이 남고, 인하 대상 구간이 없으면 변경 이력은 없는지 확인한다.
  @Test
  @DisplayName("인하하면 구간 생성 이력이 남는다")
  void reduce_recordsCreateHistory() {
    PolicyResponse created = reduce("0.03", NOV_20);

    List<CashbackPolicyHistory> histories = historyRepository.findAll();
    assertThat(histories).hasSize(1);
    CashbackPolicyHistory history = histories.getFirst();
    assertThat(history.getPolicyId()).isEqualTo(created.policyId());
    assertThat(history.getAction()).isEqualTo(PolicyHistoryAction.CREATE);
    assertThat(history.getChangedBy()).isEqualTo(OPERATOR_ID);
    assertThat(history.getReason()).isEqualTo("테스트 사유");
    assertThat(history.getBeforeRate()).isNull();
    assertThat(history.getAfterRate().toPlainString()).isEqualTo("0.0300");
    assertThat(history.getAfterStartsAt()).isEqualTo(NOV_20);
    assertThat(history.getAfterEndsAt()).isEqualTo(DEC_1);
  }

  // 목적: 월 기본 요율로 대체되는 구간이 없을 때 적용 요율이 월 기본 요율이고, 월 예산도 없으면 비어 있는지 확인한다.
  @Test
  @DisplayName("구간이 없으면 월 기본 요율을 적용하고 월 예산도 없으면 비어 있다")
  void effectiveRate_fallsBackToBaseRate() {
    assertThat(policyService.effectiveRate(LocalDateTime.of(2026, 11, 30, 23, 59)))
        .contains(new BigDecimal("0.0500"));
    assertThat(policyService.effectiveRate(LocalDateTime.of(2026, 12, 5, 0, 0))).isEmpty();
  }

  // 목적: 없는 ID 를 조회하면 정책 없음 에러가 나는지 확인한다.
  @Test
  @DisplayName("없는 정책을 조회하면 CSH-001")
  void get_notFound() {
    assertThatThrownBy(() -> policyService.get(999_999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.POLICY_NOT_FOUND);
  }

  // 목적: 목록이 시작이 늦은 순으로 나오고 단건 조회가 같은 값을 돌려주는지 확인한다.
  @Test
  @DisplayName("목록은 시작이 늦은 순이다")
  void findAll_orderedByStartsAtDesc() {
    PolicyResponse nov = reduce("0.04", NOV_20);
    budgetService.putMonthly(2026, 12, new MonthlyBudgetRequest(1_000_000L, null));
    PolicyResponse dec = reduce("0.03", DEC_10);

    assertThat(policyService.findAll()).containsExactly(dec, nov);
    assertThat(policyService.get(nov.policyId())).isEqualTo(nov);
  }
}
