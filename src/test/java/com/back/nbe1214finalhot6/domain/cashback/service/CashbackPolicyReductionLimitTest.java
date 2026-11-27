package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicyHistory;
import com.back.nbe1214finalhot6.domain.cashback.entity.PolicyHistoryAction;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설정값을 바꾼 경우(한 달 인하 2회, 예고 1일)의 요율 인하. 테스트 시계는 2026-11-18 09:00 KST 다.
 */
@IntegrationTest
@Transactional
@TestPropertySource(
    properties = {
      "cashback.max-rate-reductions-per-month=2",
      "cashback.rate-change-notice-days=1"
    })
class CashbackPolicyReductionLimitTest {

  private static final long OPERATOR_ID = 99L;

  private static final LocalDateTime NOV_20 = LocalDateTime.of(2026, 11, 20, 0, 0);
  private static final LocalDateTime NOV_25 = LocalDateTime.of(2026, 11, 25, 0, 0);
  private static final LocalDateTime NOV_28 = LocalDateTime.of(2026, 11, 28, 0, 0);
  private static final LocalDateTime DEC_1 = LocalDateTime.of(2026, 12, 1, 0, 0);

  @Autowired private CashbackPolicyService policyService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackPolicyRepository policyRepository;
  @Autowired private CashbackPolicyHistoryRepository historyRepository;

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

  // 목적: 인하한 구간 위에서 다시 인하하면 기존 구간이 적용 시각에 끝나고 새 구간이 이어지며(경계 오적용 없음),
  // 변경 이력이 구간 종료 변경 1건과 생성 1건으로 남는지, 횟수를 넘으면 거부되는지 확인한다.
  @Test
  @DisplayName("연속 인하하면 기존 구간이 적용 시각에 끝나고 새 구간이 이어지며 이력이 남는다")
  void reduce_chainedReductions() {
    PolicyResponse first = reduce("0.04", NOV_20);
    PolicyResponse second = reduce("0.03", NOV_25);

    CashbackPolicy shortened = policyRepository.findById(first.policyId()).orElseThrow();
    assertThat(shortened.getEndsAt()).isEqualTo(NOV_25);
    assertThat(second.startsAt()).isEqualTo(NOV_25);
    assertThat(second.endsAt()).isEqualTo(DEC_1);
    assertThat(policyService.effectiveRate(NOV_25.minusSeconds(1)))
        .contains(new BigDecimal("0.0400"));
    assertThat(policyService.effectiveRate(NOV_25)).contains(new BigDecimal("0.0300"));

    List<CashbackPolicyHistory> forFirst =
        historyRepository.findAllByPolicyIdOrderByIdAsc(first.policyId());
    assertThat(forFirst).extracting(CashbackPolicyHistory::getAction)
        .containsExactly(PolicyHistoryAction.CREATE, PolicyHistoryAction.UPDATE);
    CashbackPolicyHistory update = forFirst.get(1);
    assertThat(update.getBeforeEndsAt()).isEqualTo(DEC_1);
    assertThat(update.getAfterEndsAt()).isEqualTo(NOV_25);
    assertThat(update.getAfterRate().toPlainString()).isEqualTo("0.0400");
    assertThat(historyRepository.count()).isEqualTo(3);

    assertReduceError("0.02", NOV_28, CashbackErrorCode.RATE_REDUCTION_LIMIT_EXCEEDED);
    assertThat(policyRepository.count()).isEqualTo(2);
  }

  // 목적: 연속 인하에서도 현재(인하된) 요율보다 높거나 같은 요율은 거부되는지 확인한다.
  @Test
  @DisplayName("인하한 구간의 요율보다 낮아야 다시 인하할 수 있다")
  void reduce_mustBeLowerThanReducedSegment() {
    reduce("0.03", NOV_20);

    assertReduceError("0.04", NOV_25, CashbackErrorCode.INVALID_POLICY);
    assertReduceError("0.03", NOV_25, CashbackErrorCode.INVALID_POLICY);
  }

  // 목적: 예고 일수(1일)가 적용되어 현재 분 + 1일 이전은 거부되고 그 시각부터 허용되는지 확인한다.
  @Test
  @DisplayName("적용 시각은 현재 분에 예고 일수를 더한 시각 이후여야 한다")
  void reduce_respectsNoticeDays() {
    assertReduceError(
        "0.03", LocalDateTime.of(2026, 11, 19, 8, 59), CashbackErrorCode.INVALID_POLICY);

    assertThat(reduce("0.03", LocalDateTime.of(2026, 11, 19, 9, 0)).startsAt())
        .isEqualTo(LocalDateTime.of(2026, 11, 19, 9, 0));
  }

  // 목적: 적용 시각 뒤에 이미 예약된 구간이 있어 새 구간이 겹치면 CSH-002 로 거부되고 저장되지 않는지 확인한다.
  @Test
  @DisplayName("뒤에 있는 구간과 겹치면 CSH-002 이고 저장하지 않는다")
  void reduce_overlapRejected() {
    // 인하 API 밖에서 들어온 구간(이전 데이터)을 흉내 낸다
    policyRepository.save(
        CashbackPolicy.create(
            new BigDecimal("0.02"), NOV_25, DEC_1, LocalDateTime.of(2026, 11, 18, 9, 0)));

    assertReduceError("0.03", NOV_20, CashbackErrorCode.POLICY_PERIOD_OVERLAP);

    assertThat(policyRepository.count()).isEqualTo(1);
    assertThat(historyRepository.count()).isZero();
  }
}
