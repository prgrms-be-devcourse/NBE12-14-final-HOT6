package com.back.nbe1214finalhot6.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PIN 검증. 5회 잠금, 호출한 쪽이 롤백돼도 남는 실패 횟수, 동시에 틀려도 5를 넘지 않는 것을 실제 DB 로 확인한다.
 *
 * <p>실패 횟수는 새 트랜잭션으로 저장하므로, 이 테스트는 테스트 전체를 트랜잭션으로 감싸지 않고 직접 정리한다.
 */
@IntegrationTest
class PinServiceTest {

  private static final String PIN = "123456";
  private static final String WRONG = "000000";

  @Autowired PinService pinService;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired MutableClock clock;

  // 이 테스트가 만든 회원만 지운다(다른 테스트의 데이터를 건드리지 않음)
  private final List<Long> userIds = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    userRepository.deleteAllById(userIds);
    userIds.clear();
  }

  private User save(String email, String phone) {
    User user =
        userRepository.save(
            User.create("홍길동", email, phone, "pw", passwordEncoder.encode(PIN), UserRole.USER));
    userIds.add(user.getId());
    return user;
  }

  private void lockByFiveFailures(User user) {
    for (int i = 0; i < 4; i++) {
      assertFails(() -> pinService.verify(user.getId(), WRONG), AuthErrorCode.PIN_MISMATCH);
    }
    assertFails(() -> pinService.verify(user.getId(), WRONG), AuthErrorCode.PIN_LOCKED);
    assertThat(locked(user.getId())).isTrue();
  }

  private int failCount(Long userId) {
    return jdbcTemplate.queryForObject(
        "select pin_fail_count from users where id = ?", Integer.class, userId);
  }

  private boolean locked(Long userId) {
    return jdbcTemplate.queryForObject(
        "select pin_locked_at is not null from users where id = ?", Boolean.class, userId);
  }

  private void assertFails(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }

  @Test
  @DisplayName("맞는 PIN 이면 통과하고 실패 횟수는 0이다")
  void correctPin() {
    User user = save("p1@devpay.com", "01055550001");

    pinService.verify(user.getId(), PIN);

    assertThat(failCount(user.getId())).isZero();
    assertThat(locked(user.getId())).isFalse();
  }

  // 목적: 틀리면 AUTH-005 와 남은 횟수를 알려 주고 실패 횟수가 올라가는지 확인한다.
  @Test
  @DisplayName("틀리면 AUTH-005 이고 메시지에 남은 횟수가 있다")
  void wrongPin() {
    User user = save("p2@devpay.com", "01055550002");

    assertThatThrownBy(() -> pinService.verify(user.getId(), WRONG))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.PIN_MISMATCH);
              assertThat(e.getMessage()).contains("잔여 4회");
            });
    assertThat(failCount(user.getId())).isEqualTo(1);
    assertThat(locked(user.getId())).isFalse();
  }

  // 목적: 5번째로 틀리면 잠기고(AUTH-006), 잠긴 뒤에는 맞는 PIN 도 거절하며 횟수가 더 오르지 않는지 확인한다.
  @Test
  @DisplayName("5번째로 틀리면 잠기고, 잠긴 뒤에는 맞는 PIN 도 AUTH-006 이다")
  void lockAfterFiveFailures() {
    User user = save("p3@devpay.com", "01055550003");

    for (int remaining = 4; remaining >= 1; remaining--) {
      int expected = remaining;
      assertThatThrownBy(() -> pinService.verify(user.getId(), WRONG))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getMessage()).contains("잔여 " + expected + "회"));
    }
    assertFails(() -> pinService.verify(user.getId(), WRONG), AuthErrorCode.PIN_LOCKED);

    assertThat(failCount(user.getId())).isEqualTo(5);
    assertThat(locked(user.getId())).isTrue();

    // 잠긴 뒤에는 맞는 PIN 도, 틀린 PIN 도 거절하고 횟수는 그대로다
    assertFails(() -> pinService.verify(user.getId(), PIN), AuthErrorCode.PIN_LOCKED);
    assertFails(() -> pinService.verify(user.getId(), WRONG), AuthErrorCode.PIN_LOCKED);
    assertThat(failCount(user.getId())).isEqualTo(5);
  }

  // 목적: 잠금은 10분 동안만 유지되는지 경계를 확인한다. 9분 59초에는 맞는 PIN 도 거절하고, 10분이 되면 풀려서 통과해야 한다.
  // (임시 방식. MVP 이후에는 이메일 인증으로 해제(U7)로 바꾼다)
  @Test
  @DisplayName("잠긴 지 9분 59초까지는 거절하고, 10분이 지나면 풀려서 맞는 PIN 이 통과한다")
  void lockExpiresAfterTenMinutes() {
    User user = save("p8@devpay.com", "01055550008");
    lockByFiveFailures(user);

    clock.advance(Duration.ofMinutes(9).plusSeconds(59));
    assertFails(() -> pinService.verify(user.getId(), PIN), AuthErrorCode.PIN_LOCKED);
    assertThat(locked(user.getId())).isTrue();

    clock.advance(Duration.ofSeconds(1)); // 정확히 10분
    pinService.verify(user.getId(), PIN);

    assertThat(locked(user.getId())).isFalse();
    assertThat(failCount(user.getId())).isZero();
  }

  // 목적: 풀린 뒤에는 실패 횟수가 처음부터 다시 시작하는지(남은 횟수 4회), 다시 5번 틀리면 다시 잠기는지 확인한다.
  @Test
  @DisplayName("풀린 뒤 틀리면 횟수가 처음부터 다시 시작하고, 5번 틀리면 다시 잠긴다")
  void startsOverAfterUnlock() {
    User user = save("p9@devpay.com", "01055550009");
    lockByFiveFailures(user);
    clock.advance(Duration.ofMinutes(11));

    assertThatThrownBy(() -> pinService.verify(user.getId(), WRONG))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getMessage()).contains("잔여 4회"));
    assertThat(failCount(user.getId())).isEqualTo(1);
    assertThat(locked(user.getId())).isFalse();

    for (int i = 0; i < 3; i++) {
      assertFails(() -> pinService.verify(user.getId(), WRONG), AuthErrorCode.PIN_MISMATCH);
    }
    assertFails(() -> pinService.verify(user.getId(), WRONG), AuthErrorCode.PIN_LOCKED);
    assertThat(failCount(user.getId())).isEqualTo(5);
    assertThat(locked(user.getId())).isTrue();
    // 다시 잠겼으니 10분이 새로 시작된다
    clock.advance(Duration.ofMinutes(9));
    assertFails(() -> pinService.verify(user.getId(), PIN), AuthErrorCode.PIN_LOCKED);
  }

  // 목적: 맞는 PIN 이면 쌓인 실패 횟수가 0으로 돌아가서, 이후 다시 5번의 기회가 생기는지 확인한다.
  @Test
  @DisplayName("맞는 PIN 이면 실패 횟수가 0으로 돌아간다")
  void successResetsFailCount() {
    User user = save("p4@devpay.com", "01055550004");
    assertFails(() -> pinService.verify(user.getId(), WRONG), AuthErrorCode.PIN_MISMATCH);
    assertFails(() -> pinService.verify(user.getId(), WRONG), AuthErrorCode.PIN_MISMATCH);
    assertThat(failCount(user.getId())).isEqualTo(2);

    pinService.verify(user.getId(), PIN);

    assertThat(failCount(user.getId())).isZero();
    // 다시 틀리면 남은 횟수는 처음처럼 4회다
    assertThatThrownBy(() -> pinService.verify(user.getId(), WRONG))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getMessage()).contains("잔여 4회"));
  }

  // 목적: 호출한 쪽(예: 결제)이 PIN 오류로 롤백돼도 실패 횟수는 남는지 확인한다.
  // 남지 않으면 결제를 계속 실패시키며 PIN 을 무한히 시도할 수 있다.
  @Test
  @DisplayName("호출한 쪽 트랜잭션이 롤백돼도 실패 횟수는 남는다")
  void failCountSurvivesCallerRollback() {
    User user = save("p5@devpay.com", "01055550005");
    TransactionTemplate outer = new TransactionTemplate(transactionManager);

    assertThatThrownBy(
            () ->
                outer.executeWithoutResult(
                    status -> {
                      assertFails(
                          () -> pinService.verify(user.getId(), WRONG), AuthErrorCode.PIN_MISMATCH);
                      throw new IllegalStateException("결제가 실패해 롤백");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(failCount(user.getId())).isEqualTo(1);
  }

  // 목적: 동시에 여러 번 틀려도 실패 횟수가 5를 넘지 않고 잠기는지 확인한다.
  // 읽고 더하는 방식이면 동시에 읽어 같은 값에서 올려 횟수가 어긋나거나 5번 넘게 시도할 수 있다.
  @Test
  @DisplayName("동시에 10번 틀려도 실패 횟수는 5이고 잠긴다")
  void concurrentFailuresStopAtFive() throws Exception {
    User user = save("p6@devpay.com", "01055550006");
    int threads = 10;
    CyclicBarrier startTogether = new CyclicBarrier(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    int mismatch = 0;
    int locked = 0;
    try {
      List<Future<ErrorCode>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(
            pool.submit(
                () -> {
                  startTogether.await(10, TimeUnit.SECONDS);
                  try {
                    pinService.verify(user.getId(), WRONG);
                    return null;
                  } catch (BusinessException e) {
                    return e.getErrorCode();
                  }
                }));
      }
      for (Future<ErrorCode> future : futures) {
        ErrorCode code = future.get(60, TimeUnit.SECONDS);
        if (code == AuthErrorCode.PIN_MISMATCH) {
          mismatch++;
        } else if (code == AuthErrorCode.PIN_LOCKED) {
          locked++;
        }
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(failCount(user.getId())).isEqualTo(5);
    assertThat(locked(user.getId())).isTrue();
    assertThat(mismatch + locked).isEqualTo(threads); // 전부 거절됨
    assertThat(mismatch).isLessThanOrEqualTo(4); // 남은 횟수를 안내한 요청은 4개 이하
  }

  @Test
  @DisplayName("탈퇴한 회원이면 AUTH-004, 없는 회원이면 COM-009 이다")
  void withdrawnOrUnknown() {
    User user = save("p7@devpay.com", "01055550007");
    jdbcTemplate.update("update users set status = 'WITHDRAWN' where id = ?", user.getId());

    assertFails(() -> pinService.verify(user.getId(), PIN), AuthErrorCode.WITHDRAWN_USER);
    assertFails(() -> pinService.verify(999_999L, PIN), CommonErrorCode.RESOURCE_NOT_FOUND);
  }
}
