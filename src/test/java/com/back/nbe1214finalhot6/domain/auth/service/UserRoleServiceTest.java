package com.back.nbe1214finalhot6.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class UserRoleServiceTest {

  @Autowired UserRoleService userRoleService;
  @Autowired UserRepository userRepository;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired PlatformTransactionManager transactionManager;

  @AfterEach
  void cleanUp() {
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("활동 중인 점주는 통과한다")
  void owner() {
    User user = save("a@devpay.com", "01011110001", UserRole.OWNER);

    assertThatCode(() -> userRoleService.requireOwner(user.getId())).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"USER", "ADMIN"})
  @DisplayName("점주가 아니면 COM-008 이고 역할은 바뀌지 않는다")
  void notOwner(UserRole role) {
    User user = save("a@devpay.com", "01011110001", role);

    assertErrorCode(() -> userRoleService.requireOwner(user.getId()), CommonErrorCode.FORBIDDEN);

    assertThat(roleOf(user.getId())).isEqualTo(role.name());
  }

  @Test
  @DisplayName("없는 회원이거나 탈퇴한 점주면 COM-009 이다")
  void unknownOrWithdrawn() {
    assertErrorCode(() -> userRoleService.requireOwner(999_999L), CommonErrorCode.RESOURCE_NOT_FOUND);

    User user = save("a@devpay.com", "01011110001", UserRole.OWNER);
    jdbcTemplate.update("update users set status = 'WITHDRAWN' where id = ?", user.getId());

    assertErrorCode(() -> userRoleService.requireOwner(user.getId()), CommonErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  @DisplayName("잠금 확인도 활동 중인 점주만 통과하고 에러는 같다")
  void lockChecksOwner() {
    User owner = save("a@devpay.com", "01011110001", UserRole.OWNER);
    User member = save("b@devpay.com", "01011110002", UserRole.USER);
    TransactionTemplate tx = new TransactionTemplate(transactionManager);

    assertThatCode(
            () -> tx.executeWithoutResult(s -> userRoleService.lockActiveOwner(owner.getId())))
        .doesNotThrowAnyException();
    assertErrorCode(
        () -> tx.executeWithoutResult(s -> userRoleService.lockActiveOwner(member.getId())),
        CommonErrorCode.FORBIDDEN);
    assertErrorCode(
        () -> tx.executeWithoutResult(s -> userRoleService.lockActiveOwner(999_999L)),
        CommonErrorCode.RESOURCE_NOT_FOUND);
  }

  // 목적: 트랜잭션 밖에서 부르면 잠금이 바로 풀려 의미가 없으므로 거절되는지 확인한다.
  @Test
  @DisplayName("트랜잭션 없이 잠금 확인을 부르면 거절한다")
  void lockNeedsTransaction() {
    User owner = save("a@devpay.com", "01011110001", UserRole.OWNER);

    assertThatThrownBy(() -> userRoleService.lockActiveOwner(owner.getId()))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  // 목적: 먼저 잠근 쪽(탈퇴)이 끝날 때까지 뒤의 확인(가맹점 등록)이 기다리고, 끝난 뒤의 상태를 보는지 확인한다.
  @Test
  @DisplayName("먼저 잠근 트랜잭션이 끝날 때까지 기다렸다가 바뀐 상태로 판단한다")
  void secondWaitsAndSeesLatest() throws Exception {
    User owner = save("a@devpay.com", "01011110001", UserRole.OWNER);
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    CompletableFuture<Void> withdrawal =
        CompletableFuture.runAsync(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      userRoleService.lockActiveOwner(owner.getId());
                      locked.countDown();
                      await(release);
                      jdbcTemplate.update(
                          "update users set status = 'WITHDRAWN' where id = ?", owner.getId());
                    }));
    assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

    CompletableFuture<Void> registration =
        CompletableFuture.runAsync(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      // 같은 트랜잭션에서 먼저 읽어 둔 객체가 있어도 잠금 확인은 DB 의 현재 값을 본다
                      userRepository.findById(owner.getId());
                      userRoleService.lockActiveOwner(owner.getId());
                    }));
    try {
      assertThatThrownBy(() -> registration.get(500, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);
    } finally {
      release.countDown();
    }

    withdrawal.get(5, TimeUnit.SECONDS);
    assertThatThrownBy(() -> registration.get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(BusinessException.class)
        .cause()
        .extracting("errorCode")
        .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private String roleOf(Long userId) {
    return jdbcTemplate.queryForObject("select role from users where id = ?", String.class, userId);
  }

  private User save(String email, String phone, UserRole role) {
    return userRepository.save(User.create("홍길동", email, phone, "pw-hash", "pin-hash", role));
  }

  private void assertErrorCode(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }
}
