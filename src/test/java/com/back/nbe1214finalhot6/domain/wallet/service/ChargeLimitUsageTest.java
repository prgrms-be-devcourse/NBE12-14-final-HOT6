package com.back.nbe1214finalhot6.domain.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChargeLimitUsageTest {

  // 기본 한도: 보유 200만원, 월 충전 100만원
  private static final long HOLDING = 2_000_000L;
  private static final long MONTHLY = 1_000_000L;

  @Test
  @DisplayName("보유 한도가 더 빡빡하면 보유 한도 기준으로 남은 충전 가능액을 구한다")
  void holdingLimitIsTighter() {
    ChargeLimitUsage usage = new ChargeLimitUsage(HOLDING, MONTHLY, 0L);

    assertThat(usage.chargeableAmount(1_800_000L)).isEqualTo(200_000L);
  }

  @Test
  @DisplayName("월 충전 한도가 더 빡빡하면 월 한도 기준으로 남은 충전 가능액을 구한다")
  void monthlyLimitIsTighter() {
    ChargeLimitUsage usage = new ChargeLimitUsage(HOLDING, MONTHLY, 700_000L);

    assertThat(usage.chargeableAmount(0L)).isEqualTo(300_000L);
  }

  @Test
  @DisplayName("한도를 이미 넘은 상태면 음수가 아니라 0이다")
  void overLimit_isZero() {
    // 보유 한도를 100만원으로 낮췄는데 잔액이 150만원인 경우
    assertThat(new ChargeLimitUsage(1_000_000L, MONTHLY, 0L).chargeableAmount(1_500_000L)).isZero();
    // 이번 달 충전이 월 한도를 넘은 경우
    assertThat(new ChargeLimitUsage(HOLDING, MONTHLY, 1_200_000L).chargeableAmount(0L)).isZero();
  }

  @Test
  @DisplayName("한도와 딱 맞으면 0이고, 아직 충전한 적이 없으면 두 한도 중 작은 값이다")
  void boundaries() {
    assertThat(new ChargeLimitUsage(HOLDING, MONTHLY, MONTHLY).chargeableAmount(0L)).isZero();
    assertThat(new ChargeLimitUsage(HOLDING, MONTHLY, 0L).chargeableAmount(0L))
        .isEqualTo(MONTHLY);
  }
}
