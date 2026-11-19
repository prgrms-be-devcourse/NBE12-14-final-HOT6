package com.back.nbe1214finalhot6.domain.cashback.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CashbackPolicyTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 0, 0);
  private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 0, 0);
  private static final LocalDateTime END = LocalDateTime.of(2026, 12, 1, 0, 0);

  // 목적: 정상 값으로 만들면 입력값이 그대로 담기고 요율은 소수 넷째 자리로 맞춰지는지 확인한다.
  @Test
  @DisplayName("정상 값이면 정책이 만들어지고 요율은 소수 넷째 자리로 저장된다")
  void create_valid() {
    CashbackPolicy policy = CashbackPolicy.create(new BigDecimal("0.03"), START, END, NOW);

    assertThat(policy.getRate()).isEqualByComparingTo("0.03");
    assertThat(policy.getRate().scale()).isEqualTo(CashbackPolicy.RATE_SCALE);
    assertThat(policy.getStartsAt()).isEqualTo(START);
    assertThat(policy.getEndsAt()).isEqualTo(END);
    assertThat(policy.getCreatedAt()).isEqualTo(NOW);
  }

  // 목적: 종료를 생략하면 무기한 정책으로 만들어지는지 확인한다.
  @Test
  @DisplayName("종료 시각이 없으면 무기한 정책이다")
  void create_openEnded() {
    CashbackPolicy policy = CashbackPolicy.create(new BigDecimal("0.03"), START, null, NOW);

    assertThat(policy.getEndsAt()).isNull();
  }

  // 목적: 요율 경계값(1, 소수 넷째 자리, 끝자리 0)이 허용되는지 확인한다.
  @ParameterizedTest
  @ValueSource(strings = {"1", "1.0000", "0.0001", "0.0300", "0.03000"})
  @DisplayName("요율 경계값은 허용된다")
  void create_rateBoundaryAllowed(String rate) {
    assertThatCode(() -> CashbackPolicy.create(new BigDecimal(rate), START, END, NOW))
        .doesNotThrowAnyException();
  }

  // 목적: 0 이하, 1 초과, 소수 다섯째 자리 이상의 요율이 거부되는지 확인한다.
  @ParameterizedTest
  @ValueSource(strings = {"0", "-0.01", "1.0001", "0.00001", "0.03001"})
  @DisplayName("요율이 범위를 벗어나거나 소수 넷째 자리를 넘으면 거부한다")
  void create_invalidRate(String rate) {
    assertThatThrownBy(() -> CashbackPolicy.create(new BigDecimal(rate), START, END, NOW))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_POLICY);
  }

  // 목적: 종료가 시작과 같거나 이르면 거부되는지 확인한다. 반개구간이라 같은 시각도 빈 기간이다.
  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  @DisplayName("종료가 시작보다 늦지 않으면 거부한다")
  void create_endNotAfterStart(int daysFromStart) {
    LocalDateTime end = START.plusDays(daysFromStart);

    assertThatThrownBy(() -> CashbackPolicy.create(new BigDecimal("0.03"), START, end, NOW))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_POLICY);
  }

  // 목적: 시작이 현재보다 과거면 거부되고, 현재 분(分)이나 미래는 허용되는지 확인한다.
  // 이미 지난 기간을 나중에 채우면 이력이 실제 적용 결과와 달라지기 때문이다.
  @Test
  @DisplayName("시작 시각이 현재 분보다 과거면 거부한다")
  void create_pastStartRejected() {
    LocalDateTime now = LocalDateTime.of(2026, 11, 18, 9, 30, 45);

    assertThatThrownBy(
            () ->
                CashbackPolicy.create(
                    new BigDecimal("0.03"), LocalDateTime.of(2026, 11, 18, 9, 29, 59), null, now))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_POLICY);
    assertThatCode(
            () ->
                CashbackPolicy.create(
                    new BigDecimal("0.03"), LocalDateTime.of(2026, 11, 18, 9, 30), null, now))
        .doesNotThrowAnyException();
  }

  // 목적: 필수값(요율, 시작 시각)이 없으면 NPE 가 아니라 정책 오류로 거부되는지 확인한다.
  @Test
  @DisplayName("요율이나 시작 시각이 없으면 거부한다")
  void create_missingRequired() {
    assertThatThrownBy(() -> CashbackPolicy.create(null, START, END, NOW))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_POLICY);
    assertThatThrownBy(() -> CashbackPolicy.create(new BigDecimal("0.03"), null, END, NOW))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_POLICY);
  }
}
