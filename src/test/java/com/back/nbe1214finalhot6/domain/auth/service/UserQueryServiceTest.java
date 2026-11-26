package com.back.nbe1214finalhot6.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.auth.dto.MyInfoResponse;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 내 정보 조회 서비스. DB 의 현재 값(역할, PIN 잠금, 탈퇴 여부)을 실제 DB 로 확인한다. */
@IntegrationTest
class UserQueryServiceTest {

  @Autowired UserQueryService userQueryService;
  @Autowired UserRepository userRepository;
  @Autowired JdbcTemplate jdbcTemplate;
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
            User.create("홍길동", email, phone, "pw-hash", "pin-hash", UserRole.USER));
    userIds.add(user.getId());
    return user;
  }

  @Test
  @DisplayName("이름, 이메일, 역할, PIN 잠금 여부를 돌려주고, 토큰의 역할이 같으면 재로그인은 필요 없다")
  void getMyInfo() {
    User user = save("q1@devpay.com", "01077770001");

    MyInfoResponse info = userQueryService.getMyInfo(user.getId(), UserRole.USER);

    assertThat(info.name()).isEqualTo("홍길동");
    assertThat(info.email()).isEqualTo("q1@devpay.com");
    assertThat(info.role()).isEqualTo(UserRole.USER);
    assertThat(info.pinLocked()).isFalse();
    assertThat(info.needsRelogin()).isFalse();
  }

  // 목적: 잠금 시각이 있고 10분이 지나지 않았으면 pinLocked 가 true 인지 확인한다.
  @Test
  @DisplayName("PIN 이 잠겨 있으면 pinLocked 가 true 이다")
  void pinLocked() {
    User user = save("q2@devpay.com", "01077770002");
    lockNow(user);

    assertThat(userQueryService.getMyInfo(user.getId(), UserRole.USER).pinLocked()).isTrue();
  }

  // 목적: PIN 잠금은 잠긴 지 10분이 지나면 자동으로 풀리므로 /me 도 그 기준으로 보여 주는지 경계를 확인한다.
  // (9분 59초는 잠김, 10분은 풀림. 임시 방식이며 MVP 이후 이메일 인증 해제(U7)로 대체된다)
  @Test
  @DisplayName("PIN 잠금은 잠긴 지 10분이 지나면 pinLocked 가 false 가 된다")
  void pinLockedExpiresAfterTenMinutes() {
    User user = save("q6@devpay.com", "01077770006");
    lockNow(user);

    clock.advance(Duration.ofMinutes(9).plusSeconds(59));
    assertThat(userQueryService.getMyInfo(user.getId(), UserRole.USER).pinLocked()).isTrue();

    clock.advance(Duration.ofSeconds(1)); // 정확히 10분
    assertThat(userQueryService.getMyInfo(user.getId(), UserRole.USER).pinLocked()).isFalse();
  }

  // 시험용 시계의 지금 시각으로 PIN 이 잠긴 상태를 만든다
  private void lockNow(User user) {
    jdbcTemplate.update(
        "update users set pin_locked_at = ? where id = ?", LocalDateTime.now(clock), user.getId());
  }

  // 목적: 역할은 토큰이 아니라 DB 의 현재 값이고, 토큰의 역할과 다르면 needsRelogin 이 true 인지 확인한다.
  // 역할이 바뀐 뒤(가맹점 등록으로 승격 등)에 바로 반영돼야 하고, 토큰이 새 역할을 가지면 다시 false 가 돼야 한다.
  @Test
  @DisplayName("역할은 DB 의 현재 값이고, 토큰의 역할과 다르면 재로그인이 필요하다")
  void currentRoleFromDb() {
    User user = save("q3@devpay.com", "01077770003");
    assertThat(userQueryService.getMyInfo(user.getId(), UserRole.USER).needsRelogin()).isFalse();

    jdbcTemplate.update("update users set role = 'OWNER' where id = ?", user.getId());

    // 옛 토큰(USER)으로 보면 DB 는 OWNER → 재로그인 필요
    MyInfoResponse withOldToken = userQueryService.getMyInfo(user.getId(), UserRole.USER);
    assertThat(withOldToken.role()).isEqualTo(UserRole.OWNER);
    assertThat(withOldToken.needsRelogin()).isTrue();
    // 다시 로그인해 새 토큰(OWNER)을 받으면 재로그인은 필요 없다
    assertThat(userQueryService.getMyInfo(user.getId(), UserRole.OWNER).needsRelogin()).isFalse();
  }

  // 목적: 반대 방향(역할이 낮아진 경우)도 토큰과 DB 의 역할이 다르면 needsRelogin 이 true 인지 확인한다.
  @Test
  @DisplayName("점주 토큰인데 DB 역할이 USER 로 내려갔어도 재로그인이 필요하다")
  void demotedAlsoNeedsRelogin() {
    User user = save("q5@devpay.com", "01077770005");

    MyInfoResponse info = userQueryService.getMyInfo(user.getId(), UserRole.OWNER);

    assertThat(info.role()).isEqualTo(UserRole.USER);
    assertThat(info.needsRelogin()).isTrue();
  }

  // 목적: 탈퇴한 회원은 토큰이 유효해도 404(COM-009)로 거절하는지 확인한다.
  @Test
  @DisplayName("탈퇴한 회원이나 없는 회원이면 COM-009 이다")
  void withdrawnOrUnknown() {
    User user = save("q4@devpay.com", "01077770004");
    jdbcTemplate.update("update users set status = 'WITHDRAWN' where id = ?", user.getId());

    assertThatThrownBy(() -> userQueryService.getMyInfo(user.getId(), UserRole.USER))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND));
    assertThatThrownBy(() -> userQueryService.getMyInfo(999_999L, UserRole.USER))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND));
  }
}
