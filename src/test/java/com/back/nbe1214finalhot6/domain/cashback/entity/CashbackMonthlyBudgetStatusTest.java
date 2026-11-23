package com.back.nbe1214finalhot6.domain.cashback.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

/** 월 예산의 소진율·남은 비율·대비금 경고. 배정 1,000,000 이면 대비금은 100,000, 긴급선은 50,000 이다. */
class CashbackMonthlyBudgetStatusTest {

  private static final BigDecimal EMERGENCY_RATIO = new BigDecimal("0.5");

  private CashbackMonthlyBudget budget(long allocated, long carriedIn, long reserved, long paid) {
    CashbackMonthlyBudget budget =
        CashbackMonthlyBudget.create(
            2026, 12, allocated, new BigDecimal("0.10"), new BigDecimal("0.03"));
    ReflectionTestUtils.setField(budget, "carriedInAmount", carriedIn);
    ReflectionTestUtils.setField(budget, "reservedAmount", reserved);
    ReflectionTestUtils.setField(budget, "paidAmount", paid);
    return budget;
  }

  // 목적: 소진율은 (확보 + 지급) ÷ 전체, 남은 비율은 남은 예산 ÷ 전체이고 둘의 합이 1 인지(소수 넷째 자리) 확인한다.
  @Test
  @DisplayName("소진율과 남은 비율이 확보·지급 금액으로 계산된다")
  void rates() {
    CashbackMonthlyBudget budget = budget(1_000_000L, 0L, 150_000L, 100_000L);

    assertThat(budget.usedRate()).isEqualByComparingTo("0.2500");
    assertThat(budget.remainingRate()).isEqualByComparingTo("0.7500");
    assertThat(budget.usedRate().scale()).isEqualTo(4);
  }

  // 목적: 이월 유입이 있으면 분모(전체 예산)에 포함되는지 확인한다. 배정 1,000,000 + 이월 250,000 = 1,250,000 중 500,000 사용 = 40%.
  @Test
  @DisplayName("이월 유입은 전체 예산에 포함된다")
  void rates_includeCarriedIn() {
    CashbackMonthlyBudget budget = budget(1_000_000L, 250_000L, 500_000L, 0L);

    assertThat(budget.totalBudget()).isEqualTo(1_250_000L);
    assertThat(budget.usedRate()).isEqualByComparingTo("0.4000");
    assertThat(budget.remainingRate()).isEqualByComparingTo("0.6000");
  }

  // 목적: 다음 달로 넘긴 금액(이월 유출)이 전체 예산(분모)에서 빠지는지 확인한다. 배정 1,000,000 에서 250,000 을 넘기고 150,000 을 확보했으면 전체는
  // 750,000 이고 소진율 20%, 남은 비율 80%(남은 예산 600,000)여야 한다. 넘긴 금액을 분모에 두면 소진율 15% 로 어긋난다.
  @Test
  @DisplayName("이월 유출은 전체 예산에서 빠진다")
  void rates_excludeCarriedOut() {
    CashbackMonthlyBudget budget = budget(1_000_000L, 0L, 150_000L, 0L);
    ReflectionTestUtils.setField(budget, "carriedOutAmount", 250_000L);

    assertThat(budget.totalBudget()).isEqualTo(750_000L);
    assertThat(budget.remainingAmount()).isEqualTo(600_000L);
    assertThat(budget.usedRate()).isEqualByComparingTo("0.2000");
    assertThat(budget.remainingRate()).isEqualByComparingTo("0.8000");
  }

  // 목적: 이월로 남은 예산이 줄어 대비금 이하가 되면 경고가 바뀌는지 확인한다(대비금 100,000, 남은 예산 100,000 이 공지선).
  @Test
  @DisplayName("이월 유출로 남은 예산이 줄면 경고 단계가 올라간다")
  void warning_followsCarriedOut() {
    CashbackMonthlyBudget budget = budget(1_000_000L, 0L, 0L, 0L);
    assertThat(budget.warning(EMERGENCY_RATIO)).isEqualTo(BudgetWarning.NONE);

    ReflectionTestUtils.setField(budget, "carriedOutAmount", 900_000L);

    assertThat(budget.warning(EMERGENCY_RATIO)).isEqualTo(BudgetWarning.NOTICE_LINE);
    ReflectionTestUtils.setField(budget, "carriedOutAmount", 950_000L);
    assertThat(budget.warning(EMERGENCY_RATIO)).isEqualTo(BudgetWarning.EMERGENCY_LINE);
  }

  // 목적: 나누어 떨어지지 않는 비율이 소수 넷째 자리에서 반올림되는지 확인한다. 1 ÷ 3 = 0.3333.
  @Test
  @DisplayName("나누어 떨어지지 않는 비율은 소수 넷째 자리까지 반올림한다")
  void rates_rounding() {
    CashbackMonthlyBudget budget = budget(3L, 0L, 1L, 0L);

    assertThat(budget.usedRate()).isEqualByComparingTo("0.3333");
    assertThat(budget.remainingRate()).isEqualByComparingTo("0.6667");
  }

  // 목적: 남은 예산에 따라 경고 단계가 나뉘는지, 기준선과 같은 금액은 해당 단계에 들어가는지(경계값) 확인한다. 남은 금액 = 1,000,000 - 확보.
  @ParameterizedTest(name = "남은 {0}원이면 {1}")
  @CsvSource({
    "1000000, NONE",
    "100001, NONE",
    "100000, NOTICE_LINE",
    "50001, NOTICE_LINE",
    "50000, EMERGENCY_LINE",
    "1, EMERGENCY_LINE",
    "0, EMERGENCY_LINE"
  })
  @DisplayName("남은 예산에 따라 경고 단계가 정해진다")
  void warning_byRemaining(long remaining, BudgetWarning expected) {
    CashbackMonthlyBudget budget = budget(1_000_000L, 0L, 1_000_000L - remaining, 0L);

    assertThat(budget.warning(EMERGENCY_RATIO)).isEqualTo(expected);
  }

  // 목적: 긴급선 비율 설정이 기준선에 반영되는지 확인한다. 비율 0.2 면 긴급선은 20,000 이다.
  @Test
  @DisplayName("긴급선은 설정한 비율을 따른다")
  void warning_followsEmergencyRatio() {
    CashbackMonthlyBudget budget = budget(1_000_000L, 0L, 960_000L, 0L);

    assertThat(budget.warning(new BigDecimal("0.2"))).isEqualTo(BudgetWarning.NOTICE_LINE);
    assertThat(budget.warning(new BigDecimal("0.5"))).isEqualTo(BudgetWarning.EMERGENCY_LINE);
  }

  // 목적: 전체 예산이 0 이하일 때 비율이 오류 없이 0 이 되는지 확인한다(0 으로 나누지 않음). 배정은 0 이 될 수 없어서 값을 직접 맞춘다.
  @Test
  @DisplayName("전체 예산이 0 이면 비율은 0 이다")
  void rates_zeroTotal() {
    CashbackMonthlyBudget budget = budget(1_000_000L, 0L, 0L, 0L);
    ReflectionTestUtils.setField(budget, "allocatedAmount", 0L);

    assertThat(budget.usedRate()).isEqualByComparingTo("0");
    assertThat(budget.remainingRate()).isEqualByComparingTo("0");
  }

  // 목적: 지급은 확보를 지급으로 옮기는 것이라 지급해도 남은 비율과 경고가 변하지 않는지 확인한다.
  @Test
  @DisplayName("확보를 지급으로 옮겨도 남은 비율과 경고는 같다")
  void payoutDoesNotChangeRemaining() {
    CashbackMonthlyBudget reserved = budget(1_000_000L, 0L, 920_000L, 0L);
    CashbackMonthlyBudget paid = budget(1_000_000L, 0L, 0L, 920_000L);

    assertThat(paid.remainingRate()).isEqualByComparingTo(reserved.remainingRate());
    assertThat(paid.warning(EMERGENCY_RATIO)).isEqualTo(reserved.warning(EMERGENCY_RATIO));
  }
}
