package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetWarning;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 월 예산 조회 응답의 소진율·남은 비율·경고. 12월 배정 1,000,000(대비금 100,000, 긴급선 50,000). */
@com.back.nbe1214finalhot6.support.IntegrationTest
@Transactional
class CashbackBudgetStatusViewTest {

  @Autowired private CashbackBudgetService budgetService;
  @PersistenceContext private EntityManager entityManager;

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(
        2026, 12, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
    budgetService.putMonthly(
        2026, 11, new MonthlyBudgetRequest(2_000_000L, new BigDecimal("0.03")));
  }

  private MonthlyBudgetResponse december() {
    entityManager.flush();
    entityManager.clear();
    return budgetService.getMonthly(2026, 12);
  }

  // 목적: 실제 예산 확보를 거친 뒤 단건 조회가 소진율·남은 비율·경고를 맞게 내려 주고, 경고가 공지선·긴급선 경계에서 바뀌는지 확인한다.
  @Test
  @DisplayName("확보가 쌓이면 소진율이 오르고 경고가 공지선·긴급선에서 바뀐다")
  void single_followsReservations() {
    MonthlyBudgetResponse initial = december();
    assertThat(initial.usedRate()).isEqualByComparingTo("0");
    assertThat(initial.remainingRate()).isEqualByComparingTo("1");
    assertThat(initial.warning()).isEqualTo(BudgetWarning.NONE);

    budgetService.reserveBudget(2026, 12, 900_000L);
    MonthlyBudgetResponse atNotice = december();
    assertThat(atNotice.remainingAmount()).isEqualTo(100_000L);
    assertThat(atNotice.usedRate()).isEqualByComparingTo("0.9");
    assertThat(atNotice.remainingRate()).isEqualByComparingTo("0.1");
    assertThat(atNotice.warning()).isEqualTo(BudgetWarning.NOTICE_LINE);

    budgetService.reserveBudget(2026, 12, 50_000L);
    assertThat(december().warning()).isEqualTo(BudgetWarning.EMERGENCY_LINE);

    budgetService.releaseBudget(2026, 12, 1L);
    assertThat(december().warning()).isEqualTo(BudgetWarning.NOTICE_LINE);
  }

  // 목적: 목록 조회에도 같은 필드가 월별로 맞게 내려가는지 확인한다(11월은 사용 없음, 12월은 긴급선).
  @Test
  @DisplayName("목록 조회에도 월마다 같은 필드가 내려간다")
  void list_hasSameFields() {
    budgetService.reserveBudget(2026, 12, 960_000L);
    entityManager.flush();
    entityManager.clear();

    var list = budgetService.listMonthly(2026);

    assertThat(list).hasSize(2);
    assertThat(list.get(0).budgetMonth()).isEqualTo(11);
    assertThat(list.get(0).warning()).isEqualTo(BudgetWarning.NONE);
    assertThat(list.get(0).usedRate()).isEqualByComparingTo("0");
    assertThat(list.get(1).budgetMonth()).isEqualTo(12);
    assertThat(list.get(1).warning()).isEqualTo(BudgetWarning.EMERGENCY_LINE);
    assertThat(list.get(1).usedRate()).isEqualByComparingTo("0.96");
  }
}
