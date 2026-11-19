package com.back.nbe1214finalhot6.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupRequest;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 같은 이메일·전화번호로 동시에 가입할 때 DB unique 제약으로 한 건만 성공하는지 확인한다.
 *
 * <p>요청이 겹치지 않아 사전 중복 확인에서 걸러진 경우에도 통과하므로, DB 제약 위반을 중복 에러로 바꾸는 분기는 {@link
 * AuthServiceTest}에서 확인한다.
 */
@IntegrationTest
class AuthServiceConcurrencyTest {

  private static final int THREADS = 5;

  @Autowired AuthService authService;
  @Autowired UserRepository userRepository;

  @AfterEach
  void cleanUp() {
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("같은 이메일로 동시에 가입하면 한 건만 성공하고 나머지는 이메일 중복(409)이다")
  void 동시가입_같은이메일() throws Exception {
    List<Callable<Object>> tasks = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      // 이메일은 같고 전화번호는 서로 다르다
      SignupRequest request =
          new SignupRequest(
              "홍길동", "same@devpay.com", "Passw0rd!", "0101234567" + i, "123456");
      tasks.add(() -> authService.signup(request));
    }

    Results results = runConcurrently(tasks);

    assertThat(results.successes).isEqualTo(1);
    assertThat(results.failures).hasSize(THREADS - 1);
    assertThat(results.failures)
        .allSatisfy(
            e -> {
              assertThat(e).isInstanceOf(BusinessException.class);
              assertThat(((BusinessException) e).getErrorCode())
                  .isEqualTo(AuthErrorCode.DUPLICATE_EMAIL);
            });
    assertThat(userRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("같은 전화번호로 동시에 가입하면 한 건만 성공하고 나머지는 전화번호 중복(409)이다")
  void 동시가입_같은전화번호() throws Exception {
    List<Callable<Object>> tasks = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      // 전화번호는 같고 이메일은 서로 다르다
      SignupRequest request =
          new SignupRequest("홍길동", "user" + i + "@devpay.com", "Passw0rd!", "01012345678", "123456");
      tasks.add(() -> authService.signup(request));
    }

    Results results = runConcurrently(tasks);

    assertThat(results.successes).isEqualTo(1);
    assertThat(results.failures).hasSize(THREADS - 1);
    assertThat(results.failures)
        .allSatisfy(
            e -> {
              assertThat(e).isInstanceOf(BusinessException.class);
              assertThat(((BusinessException) e).getErrorCode())
                  .isEqualTo(AuthErrorCode.DUPLICATE_PHONE);
            });
    assertThat(userRepository.count()).isEqualTo(1);
  }

  /** 모든 작업을 래치로 묶어 동시에 출발시킨다. */
  private Results runConcurrently(List<Callable<Object>> tasks) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
    CountDownLatch ready = new CountDownLatch(tasks.size());
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Throwable>> futures = new ArrayList<>();

    for (Callable<Object> task : tasks) {
      futures.add(
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                try {
                  task.call();
                  return null;
                } catch (Throwable t) {
                  return t;
                }
              }));
    }

    ready.await();
    start.countDown();

    Results results = new Results();
    for (Future<Throwable> future : futures) {
      Throwable failure = future.get();
      if (failure == null) {
        results.successes++;
      } else {
        results.failures.add(failure);
      }
    }
    executor.shutdown();
    return results;
  }

  private static class Results {
    int successes;
    final List<Throwable> failures = new ArrayList<>();
  }
}
