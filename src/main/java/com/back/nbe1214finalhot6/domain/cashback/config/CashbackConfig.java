package com.back.nbe1214finalhot6.domain.cashback.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CashbackProperties.class)
public class CashbackConfig {

  /** 월·날짜·배치 시각의 기준 시간대. */
  public static final ZoneId KST = ZoneId.of("Asia/Seoul");

  // 시간 의존 로직(월 경계, 정시 중단)을 테스트에서 고정할 수 있게 Clock 을 주입해서 쓴다.
  // 다른 도메인이나 테스트의 "clock" 빈과 이름이 겹치지 않게 cashbackClock 으로 둔다.
  @Bean
  @ConditionalOnMissingBean(Clock.class)
  public Clock cashbackClock() {
    return Clock.system(KST);
  }
}
