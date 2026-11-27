package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

/** 인하할 때 근거를 모으는 순서. 테스트 시계는 2026-11-18 09:00 KST 이고 11월 기본 요율은 5% 다. */
@IntegrationTest
@Transactional
class CashbackPolicyBasisOrderTest {

  private static final LocalDateTime NOV_20 = LocalDateTime.of(2026, 11, 20, 0, 0);

  @Autowired private CashbackPolicyService policyService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoSpyBean private CashbackPolicyBasisCollector basisCollector;
  @MockitoSpyBean private CashbackMonthlyBudgetRepository monthlyBudgetRepository;

  @BeforeEach
  void setUp() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.05")));
  }

  private static RateReductionRequest request(String rate) {
    return new RateReductionRequest(new BigDecimal(rate), NOV_20, "사유");
  }

  // 목적: 결제가 기다리는 시간이 늘지 않도록, 예산 행을 잠그기 전에 근거를 모으는지 확인한다.
  @Test
  @DisplayName("예산 행을 잠그기 전에 근거를 모은다")
  void collectsBeforeLock() {
    policyService.reduceRate(request("0.03"), 99L);

    InOrder order = inOrder(basisCollector, monthlyBudgetRepository);
    order.verify(basisCollector).collect();
    order.verify(monthlyBudgetRepository).findWithLockByBudgetYearAndBudgetMonth(anyInt(), anyInt());
  }

  // 목적: 근거를 모은 뒤 잠그기 전에 기본 요율이 바뀌면, 먼저 읽은 값이 아니라 바뀐 값으로 판단하는지 확인한다.
  @Test
  @DisplayName("잠근 뒤의 기본 요율로 판단한다")
  void usesValueReadAfterLock() {
    doAnswer(
            invocation -> {
              Object basis = invocation.callRealMethod();
              jdbcTemplate.update(
                  "update cashback_monthly_budget set base_rate = 0.02"
                      + " where budget_year = 2026 and budget_month = 11");
              return basis;
            })
        .when(basisCollector)
        .collect();

    assertThatThrownBy(() -> policyService.reduceRate(request("0.03"), 99L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("보다 낮아야");
  }
}
