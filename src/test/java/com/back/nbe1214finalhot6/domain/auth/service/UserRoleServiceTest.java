package com.back.nbe1214finalhot6.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class UserRoleServiceTest {

  @Autowired UserRoleService userRoleService;
  @Autowired UserRepository userRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("사용자를 점주로 올리면 DB 에 반영된다")
  void promoteToOwner() {
    User user = save("a@devpay.com", "01011110001", UserRole.USER);

    userRoleService.promoteToOwner(user.getId());

    assertThat(roleOf(user.getId())).isEqualTo("OWNER");
  }

  @Test
  @DisplayName("이미 점주면 아무 일도 없다")
  void alreadyOwner() {
    User user = save("a@devpay.com", "01011110001", UserRole.OWNER);

    userRoleService.promoteToOwner(user.getId());

    assertThat(roleOf(user.getId())).isEqualTo("OWNER");
  }

  @Test
  @DisplayName("운영자는 점주로 올릴 수 없고(COM-008) 역할도 바뀌지 않는다")
  void adminIsForbidden() {
    User admin = save("admin@devpay.com", "01011110002", UserRole.ADMIN);

    assertErrorCode(() -> userRoleService.promoteToOwner(admin.getId()), CommonErrorCode.FORBIDDEN);

    assertThat(roleOf(admin.getId())).isEqualTo("ADMIN");
  }

  @Test
  @DisplayName("없는 회원이거나 탈퇴한 회원이면 COM-009 이다")
  void unknownOrWithdrawn() {
    assertErrorCode(() -> userRoleService.promoteToOwner(999_999L), CommonErrorCode.RESOURCE_NOT_FOUND);

    User user = save("a@devpay.com", "01011110001", UserRole.USER);
    jdbcTemplate.update("update users set status = 'WITHDRAWN' where id = ?", user.getId());

    assertErrorCode(() -> userRoleService.promoteToOwner(user.getId()), CommonErrorCode.RESOURCE_NOT_FOUND);
    assertThat(roleOf(user.getId())).isEqualTo("USER");
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
