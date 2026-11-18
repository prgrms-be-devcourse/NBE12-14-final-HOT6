package com.back.nbe1214finalhot6.domain.cashback.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReserveResultTest {

  @Test
  @DisplayName("제외 결과는 적립액 0, 요율 0이고 정책·지급 예정 시각이 없으며 사유를 가진다")
  void 제외결과() {
    ReserveResult result = ReserveResult.excluded(ExclusionReason.BUDGET_EXHAUSTED);

    assertThat(result.earnedAmount()).isZero();
    assertThat(result.appliedRate()).isEqualByComparingTo("0");
    assertThat(result.appliedPolicyId()).isNull();
    assertThat(result.payableAt()).isNull();
    assertThat(result.exclusionReason()).isEqualTo(ExclusionReason.BUDGET_EXHAUSTED);
  }
}
