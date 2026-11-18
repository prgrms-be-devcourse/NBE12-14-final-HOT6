package com.back.nbe1214finalhot6.domain.cashback.support;

import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 통합 테스트용 시계. 기본값은 2026-11-18 09:00 KST 이며, 테스트에서 주입받아 옮길 수 있다. */
@TestConfiguration
public class TestClockConfig {

  @Bean
  @Primary
  public MutableClock clock() {
    return new MutableClock(Instant.parse("2026-11-18T00:00:00Z"), ZoneId.of("Asia/Seoul"));
  }
}
