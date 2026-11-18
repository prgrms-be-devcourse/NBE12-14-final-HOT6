package com.back.nbe1214finalhot6.support;

import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 통합 테스트용 시계. 시작 시각은 2026-11-18 09:00 KST 이며, 테스트에서 주입받아 옮길 수 있다. */
@TestConfiguration
public class TestClockConfig {

  public static final Instant START = Instant.parse("2026-11-18T00:00:00Z");
  public static final ZoneId KST = ZoneId.of("Asia/Seoul");

  // 운영 코드의 clock 빈과 이름이 겹치면 컨텍스트가 뜨지 않으므로 testClock 으로 둔다.
  @Bean
  @Primary
  public MutableClock testClock() {
    return new MutableClock(START, KST);
  }
}
