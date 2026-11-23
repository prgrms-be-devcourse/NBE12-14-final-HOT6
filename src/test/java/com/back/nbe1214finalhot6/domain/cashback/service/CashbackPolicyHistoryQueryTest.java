package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyHistoryResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicyHistory;
import com.back.nbe1214finalhot6.domain.cashback.entity.PolicyHistoryAction;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyHistoryRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 정책 변경 이력 조회. 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class CashbackPolicyHistoryQueryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 9, 0);

  @Autowired private CashbackPolicyService policyService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackPolicyRepository policyRepository;
  @Autowired private CashbackPolicyHistoryRepository historyRepository;

  private CashbackPolicy savePolicy(String rate, LocalDateTime startsAt, LocalDateTime endsAt) {
    return policyRepository.save(CashbackPolicy.create(new BigDecimal(rate), startsAt, endsAt, NOW));
  }

  private CashbackPolicyHistory saveCreated(CashbackPolicy policy, LocalDateTime changedAt) {
    return historyRepository.save(CashbackPolicyHistory.created(policy, null, changedAt));
  }

  // 목적: 요율 인하 API 가 남긴 이력이 조회에 그대로 나오는지(CREATE, 변경 전 값 없음, 변경 후 요율·기간·시각) 확인한다.
  @Test
  @DisplayName("요율 인하 후 이력에 새 구간 생성이 변경 후 값과 함께 나온다")
  void history_afterRateReduction() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
    PolicyResponse reduced =
        policyService.reduceRate(
            new RateReductionRequest(new BigDecimal("0.02"), LocalDateTime.of(2026, 11, 18, 12, 0)));

    PageResponse<PolicyHistoryResponse> result = policyService.findHistory(null, 0, 20);

    assertThat(result.totalElements()).isEqualTo(1);
    PolicyHistoryResponse history = result.content().get(0);
    assertThat(history.policyId()).isEqualTo(reduced.policyId());
    assertThat(history.action()).isEqualTo(PolicyHistoryAction.CREATE);
    assertThat(history.changedAt()).isEqualTo(NOW);
    assertThat(history.changedBy()).isNull();
    assertThat(history.beforeRate()).isNull();
    assertThat(history.beforeStartsAt()).isNull();
    assertThat(history.beforeEndsAt()).isNull();
    assertThat(history.afterRate()).isEqualByComparingTo("0.02");
    assertThat(history.afterStartsAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 12, 0));
    assertThat(history.afterEndsAt()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0));
  }

  // 목적: 최근 변경순으로 나오고, 같은 시각의 변경은 나중에 남긴 것이 먼저 나오는지(요율 인하가 끝난 구간 UPDATE 와 새 구간 CREATE 를 같은 시각에
  // 남김), UPDATE 는 변경 전·후 값을 모두 담는지 확인한다.
  @Test
  @DisplayName("최근 변경순이고 같은 시각이면 나중에 남긴 것이 먼저이며 UPDATE 는 전후 값을 담는다")
  void history_newestFirstWithBeforeAfter() {
    CashbackPolicy ending = savePolicy("0.03", NOW.plusHours(1), null);
    CashbackPolicyHistory first = saveCreated(ending, NOW.minusDays(1));
    CashbackPolicy before = ending.snapshot();
    ending.shortenEnd(NOW.plusDays(1));
    CashbackPolicy successor = savePolicy("0.02", NOW.plusDays(1), NOW.plusDays(5));
    CashbackPolicyHistory updated =
        historyRepository.save(CashbackPolicyHistory.updated(before, ending, null, NOW));
    CashbackPolicyHistory created = saveCreated(successor, NOW);

    var content = policyService.findHistory(null, 0, 20).content();

    assertThat(content)
        .extracting(PolicyHistoryResponse::historyId)
        .containsExactly(created.getId(), updated.getId(), first.getId());
    PolicyHistoryResponse update = content.get(1);
    assertThat(update.action()).isEqualTo(PolicyHistoryAction.UPDATE);
    assertThat(update.beforeRate()).isEqualByComparingTo("0.03");
    assertThat(update.beforeEndsAt()).isNull();
    assertThat(update.afterRate()).isEqualByComparingTo("0.03");
    assertThat(update.afterEndsAt()).isEqualTo(NOW.plusDays(1));
  }

  // 목적: policyId 로 한 구간의 이력만 걸러지는지, 이력이 없는 ID 는 오류 없이 빈 목록인지 확인한다.
  @Test
  @DisplayName("정책 ID 로 한 구간의 이력만 볼 수 있고 없는 ID 는 빈 목록이다")
  void history_filtersByPolicyId() {
    CashbackPolicy a = savePolicy("0.03", NOW.plusDays(1), NOW.plusDays(2));
    CashbackPolicy b = savePolicy("0.02", NOW.plusDays(2), NOW.plusDays(3));
    saveCreated(a, NOW);
    CashbackPolicyHistory forB = saveCreated(b, NOW.plusMinutes(1));

    assertThat(policyService.findHistory(b.getId(), 0, 20).content())
        .extracting(PolicyHistoryResponse::historyId)
        .containsExactly(forB.getId());
    assertThat(policyService.findHistory(999_999L, 0, 20).content()).isEmpty();
    assertThat(policyService.findHistory(null, 0, 20).totalElements()).isEqualTo(2);
  }

  // 목적: 페이지 나누기와 전체 건수, 너무 큰 페이지 크기가 상한으로 줄어드는지, 이력이 없으면 빈 목록인지 확인한다.
  @Test
  @DisplayName("페이지로 나누고 크기 상한을 지키며 없으면 빈 목록이다")
  void history_paging() {
    assertThat(policyService.findHistory(null, 0, 20).content()).isEmpty();
    for (int i = 0; i < 5; i++) {
      CashbackPolicy policy = savePolicy("0.03", NOW.plusDays(i + 1), NOW.plusDays(i + 2));
      saveCreated(policy, NOW.plusMinutes(i));
    }

    PageResponse<PolicyHistoryResponse> second = policyService.findHistory(null, 1, 2);

    assertThat(second.totalElements()).isEqualTo(5);
    assertThat(second.totalPages()).isEqualTo(3);
    assertThat(second.content()).hasSize(2);
    assertThat(second.content().get(0).changedAt()).isEqualTo(NOW.plusMinutes(2));
    assertThat(policyService.findHistory(null, 0, 1_000).size())
        .isEqualTo(CashbackPolicyService.MAX_HISTORY_PAGE_SIZE);
  }
}
