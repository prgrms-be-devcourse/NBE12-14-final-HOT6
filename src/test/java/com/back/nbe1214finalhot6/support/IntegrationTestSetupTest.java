package com.back.nbe1214finalhot6.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

/** 통합 테스트 공통 설정(테스트 DB 분리, 시계)이 의도대로 동작하는지 확인한다. */
@IntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTestSetupTest {

  @Autowired Clock clock;
  @Autowired DataSource dataSource;

  @Test
  @Order(1)
  @DisplayName("통합 테스트는 개발 DB 가 아닌 테스트 전용 DB(devpay_test)에 연결된다")
  void 테스트전용DB연결() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection.getCatalog()).isEqualTo("devpay_test");
    }
  }

  @Test
  @Order(2)
  @DisplayName("테스트 시계는 한국 시간대이고 시간을 옮길 수 있다")
  void 테스트시계() {
    MutableClock mutableClock = (MutableClock) clock;

    mutableClock.advance(Duration.ofDays(3));

    assertThat(clock.getZone()).isEqualTo(TestClockConfig.KST);
    assertThat(clock.instant()).isEqualTo(TestClockConfig.START.plus(Duration.ofDays(3)));
    // 일부러 되돌리지 않는다. 다음 테스트가 초기화되는지 확인한다.
  }

  @Test
  @Order(3)
  @DisplayName("앞선 테스트가 옮긴 시계는 다음 테스트 시작 전에 초기화된다")
  void 시계는테스트사이에초기화된다() {
    assertThat(clock.instant()).isEqualTo(TestClockConfig.START);
  }
}
