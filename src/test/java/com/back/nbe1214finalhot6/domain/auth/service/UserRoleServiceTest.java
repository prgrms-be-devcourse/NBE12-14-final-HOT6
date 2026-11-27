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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
