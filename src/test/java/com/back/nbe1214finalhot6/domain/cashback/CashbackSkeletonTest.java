package com.back.nbe1214finalhot6.domain.cashback;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** cashback 설정값이 스프링 컨텍스트에서 바인딩되고, test 프로필이 스케줄러를 끄는지 확인한다. */
@IntegrationTest
class CashbackSkeletonTest {

  @Autowired CashbackProperties properties;

  @Test
  @DisplayName("test 프로필에서는 스케줄러가 꺼져 있다")
  void 테스트프로필에서스케줄러꺼짐() {
    assertThat(properties.scheduler().enabled()).isFalse();
  }

  @Test
  @DisplayName("프로필에서 바꾸지 않은 설정은 기본값이 적용된다")
  void 나머지설정은기본값() {
    assertThat(properties.contingencyRate()).isEqualByComparingTo("0.10");
    assertThat(properties.emergencyRatio()).isEqualByComparingTo("0.5");
    assertThat(properties.noticeDays()).isEqualTo(3);
    assertThat(properties.payoutDelayDays()).isEqualTo(3);
    assertThat(properties.scheduler().payoutIntervalSeconds()).isEqualTo(3600);
  }
}
