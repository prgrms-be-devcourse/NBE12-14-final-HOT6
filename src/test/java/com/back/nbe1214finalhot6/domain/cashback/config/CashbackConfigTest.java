package com.back.nbe1214finalhot6.domain.cashback.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class CashbackConfigTest {

  @Test
  @DisplayName("설정값을 주지 않으면 기본값이 적용된다")
  void 설정값기본값() {
    Binder binder = new Binder(new MapConfigurationPropertySource(Map.of()));

    CashbackProperties properties = binder.bindOrCreate("cashback", CashbackProperties.class);

    assertThat(properties.contingencyRate()).isEqualByComparingTo("0.10");
    assertThat(properties.emergencyRatio()).isEqualByComparingTo("0.5");
    assertThat(properties.noticeDays()).isEqualTo(3);
    assertThat(properties.payoutDelayDays()).isEqualTo(3);
    assertThat(properties.scheduler().enabled()).isTrue();
    assertThat(properties.scheduler().payoutIntervalSeconds()).isEqualTo(3600);
  }

  @Test
  @DisplayName("설정값을 주면 기본값 대신 그 값이 적용된다")
  void 설정값재정의() {
    Binder binder =
        new Binder(
            new MapConfigurationPropertySource(
                Map.of(
                    "cashback.contingency-rate", "0.2",
                    "cashback.scheduler.enabled", "false",
                    "cashback.scheduler.payout-interval-seconds", "10")));

    CashbackProperties properties = binder.bindOrCreate("cashback", CashbackProperties.class);

    assertThat(properties.contingencyRate()).isEqualByComparingTo("0.2");
    assertThat(properties.scheduler().enabled()).isFalse();
    assertThat(properties.scheduler().payoutIntervalSeconds()).isEqualTo(10);
  }

  @Test
  @DisplayName("기본 Clock 은 한국 시간대를 쓴다")
  void 기본시계는KST() {
    assertThat(new CashbackConfig().cashbackClock().getZone()).isEqualTo(CashbackConfig.KST);
  }
}
