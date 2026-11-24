package com.back.nbe1214finalhot6.domain.cashback.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class CashbackInitDataTest {

  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private Clock clock;

  private CashbackInitData initData(boolean enabled, long amount) {
    CashbackProperties properties =
        new CashbackProperties(
            new BigDecimal("0.10"),
            new BigDecimal("0.5"),
            3,
            3,
            0,
            1,
            null,
            null,
            new CashbackProperties.Init(enabled, amount));
    return new CashbackInitData(properties, annualBudgetRepository, budgetService, clock);
  }

  private int thisYear() {
    return Year.now(clock).getValue();
  }

  // 목적: 올해 연 예산이 없으면 설정한 금액으로 만들어지는지 확인한다.
  @Test
  @DisplayName("올해 연 예산이 없으면 설정한 금액으로 만든다")
  void createsWhenMissing() {
    initData(true, 100_000_000L).run(null);

    assertThat(annualBudgetRepository.findByBudgetYear(thisYear()))
        .get()
        .satisfies(budget -> assertThat(budget.getTotalAmount()).isEqualTo(100_000_000L));
  }

  // 목적: 운영자가 이미 넣은 값을 서버를 다시 켜도 덮어쓰지 않는지 확인한다.
  @Test
  @DisplayName("이미 있으면 총액을 덮어쓰지 않는다")
  void keepsExisting() {
    budgetService.putAnnual(thisYear(), new AnnualBudgetRequest(7_000_000L));

    initData(true, 100_000_000L).run(null);

    assertThat(annualBudgetRepository.findByBudgetYear(thisYear()))
        .get()
        .satisfies(budget -> assertThat(budget.getTotalAmount()).isEqualTo(7_000_000L));
  }

  // 목적: 꺼 두면(기본값) 아무것도 만들지 않는지 확인한다.
  @Test
  @DisplayName("꺼져 있으면 만들지 않는다")
  void disabled() {
    initData(false, 100_000_000L).run(null);

    assertThat(annualBudgetRepository.findByBudgetYear(thisYear())).isEmpty();
  }
}
