package com.back.nbe1214finalhot6.domain.cashback.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 통합 테스트 공통 설정(테스트 DB 분리, 시계)이 의도대로 동작하는지 확인한다. */
@CashbackIntegrationTest
class CashbackTestSetupTest {

  @Autowired Clock clock;
  @Autowired DataSource dataSource;

  @Test
  @DisplayName("통합 테스트는 개발 DB 가 아닌 테스트 전용 DB(devpay_test)에 연결된다")
  void 테스트전용DB연결() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection.getCatalog()).isEqualTo("devpay_test");
    }
  }

  @Test
  @DisplayName("테스트 시계는 한국 시간대이고 시간을 옮길 수 있다")
  void 테스트시계() {
    MutableClock mutableClock = (MutableClock) clock;
    Instant start = mutableClock.instant();

    mutableClock.advance(Duration.ofDays(3));

    assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    assertThat(clock.instant()).isEqualTo(start.plus(Duration.ofDays(3)));
    mutableClock.set(start);
  }
}
