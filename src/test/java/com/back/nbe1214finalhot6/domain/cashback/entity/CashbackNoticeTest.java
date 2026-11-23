package com.back.nbe1214finalhot6.domain.cashback.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class CashbackNoticeTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 9, 0);

  private CashbackPolicy policy(String rate, LocalDateTime startsAt) {
    CashbackPolicy policy =
        CashbackPolicy.create(new BigDecimal(rate), startsAt, startsAt.plusDays(10), NOW);
    ReflectionTestUtils.setField(policy, "id", 7L);
    return policy;
  }

  // 목적: 정책 변경 예고가 종류·적용 시작일(날짜만)·정책 ID 를 담고 중단 예정일은 비어 있으며, 문구에 적용 월·일과 새 요율이 들어가고 시각은 들어가지
  // 않는지 확인한다.
  @Test
  @DisplayName("정책 변경 예고는 적용일과 새 요율을 문구에 담고 시각은 쓰지 않는다")
  void policyChange_content() {
    CashbackNotice notice =
        CashbackNotice.policyChange(policy("0.05", LocalDateTime.of(2026, 12, 1, 14, 30)), NOW);

    assertThat(notice.getType()).isEqualTo(NoticeType.POLICY_CHANGE);
    assertThat(notice.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 12, 1));
    assertThat(notice.getStopAt()).isNull();
    assertThat(notice.getPolicyId()).isEqualTo(7L);
    assertThat(notice.getCreatedAt()).isEqualTo(NOW);
    assertThat(notice.getContent())
        .isEqualTo("12월 1일부터 적립률이 5%로 바뀌어요. 이미 접수된 결제는 약속한 금액 그대로 적립돼요")
        .doesNotContain("14")
        .doesNotContain(":");
  }

  // 목적: 소수 비율이 퍼센트 문자열로 바뀌는지(정수는 소수점 없이, 소수가 있으면 그대로) 확인한다.
  @ParameterizedTest(name = "{0} -> {1}%")
  @CsvSource({"0.05, 5", "0.1, 10", "0.035, 3.5", "0.0250, 2.5", "1, 100", "0.0001, 0.01"})
  @DisplayName("요율을 퍼센트로 표기한다")
  void percent(String rate, String expected) {
    assertThat(CashbackNotice.percent(new BigDecimal(rate))).isEqualTo(expected);
  }
}
