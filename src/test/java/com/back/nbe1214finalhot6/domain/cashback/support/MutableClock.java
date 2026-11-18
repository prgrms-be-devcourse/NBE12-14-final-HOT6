package com.back.nbe1214finalhot6.domain.cashback.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** 테스트에서 시간을 직접 옮길 수 있는 Clock. 월 경계·정시 중단 같은 시간 의존 로직 검증에 쓴다. */
public class MutableClock extends Clock {

  private final ZoneId zone;
  private volatile Instant instant;

  public MutableClock(Instant instant, ZoneId zone) {
    this.instant = instant;
    this.zone = zone;
  }

  public void set(Instant instant) {
    this.instant = instant;
  }

  public void advance(Duration duration) {
    this.instant = this.instant.plus(duration);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  // 시간대만 다른 복사본이며, 원본의 이후 변경은 따라가지 않는다.
  @Override
  public Clock withZone(ZoneId zone) {
    return new MutableClock(instant, zone);
  }

  @Override
  public Instant instant() {
    return instant;
  }
}
