package com.back.nbe1214finalhot6.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTest {

  private User user(UserRole role) {
    return User.create("홍길동", "u@devpay.com", "01012345678", "pw-hash", "pin-hash", role);
  }

  @Test
  @DisplayName("사용자는 점주로 올릴 수 있다")
  void promoteUser() {
    User user = user(UserRole.USER);

    user.promoteToOwner();

    assertThat(user.getRole()).isEqualTo(UserRole.OWNER);
  }

  @Test
  @DisplayName("이미 점주면 다시 올려도 그대로다")
  void promoteOwnerIsIdempotent() {
    User user = user(UserRole.OWNER);

    user.promoteToOwner();

    assertThat(user.getRole()).isEqualTo(UserRole.OWNER);
  }

  @Test
  @DisplayName("운영자는 점주로 바뀌지 않는다")
  void adminCannotBecomeOwner() {
    User admin = user(UserRole.ADMIN);

    assertThatThrownBy(admin::promoteToOwner).isInstanceOf(IllegalStateException.class);

    assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
  }

  // 목적: PIN 잠금이 정확히 10분 동안만 유지되는지 경계를 확인한다(9분 59초는 잠김, 10분은 풀림, 잠근 적 없으면 안 잠김).
  @Test
  @DisplayName("PIN 잠금은 잠긴 지 10분 전까지만 유지된다")
  void pinLockedOnlyForTenMinutes() {
    User user = User.create("홍길동", "u@devpay.com", "01012340000", "pw", "pin", UserRole.USER);
    LocalDateTime lockedAt = LocalDateTime.of(2026, 11, 24, 9, 0);

    assertThat(user.isPinLocked(lockedAt)).isFalse(); // 잠근 적 없음

    ReflectionTestUtils.setField(user, "pinLockedAt", lockedAt);
    assertThat(user.isPinLocked(lockedAt)).isTrue();
    assertThat(user.isPinLocked(lockedAt.plusMinutes(9).plusSeconds(59))).isTrue();
    assertThat(user.isPinLocked(lockedAt.plusMinutes(10))).isFalse();
    assertThat(user.isPinLocked(lockedAt.plusHours(2))).isFalse();
  }
}
