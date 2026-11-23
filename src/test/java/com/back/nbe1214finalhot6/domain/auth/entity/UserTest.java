package com.back.nbe1214finalhot6.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
