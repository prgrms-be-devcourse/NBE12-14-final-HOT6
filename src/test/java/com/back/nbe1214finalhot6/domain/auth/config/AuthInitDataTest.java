package com.back.nbe1214finalhot6.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class AuthInitDataTest {

  private static final String EMAIL = "admin-init@test.local";
  private static final String PASSWORD = "Admin1234!";
  private static final String PHONE = "01099990000";

  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private AuthInitData initData(String email, String password, String phone, String pin) {
    AuthProperties properties =
        new AuthProperties(new AuthProperties.Init(email, password, "운영자", phone, pin));
    return new AuthInitData(properties, userRepository, passwordEncoder);
  }

  private AuthInitData validInitData() {
    return initData(EMAIL, PASSWORD, PHONE, "123456");
  }

  // 목적: 설정값이 비어 있으면(환경 변수를 안 준 환경) 아무 계정도 만들지 않는지 확인한다.
  @Test
  @DisplayName("값이 비어 있으면 만들지 않는다")
  void doesNothingWhenEmpty() {
    initData("", "", "", "").run(null);

    assertThat(userRepository.existsByEmail(EMAIL)).isFalse();
    assertThat(userRepository.count()).isZero();
  }

  // 목적: 다섯 값 중 하나만 비어 있어도 만들지 않는지 확인한다(비밀번호만 빠진 설정 실수 등).
  @Test
  @DisplayName("값이 하나라도 비어 있으면 만들지 않는다")
  void doesNothingWhenAnyValueMissing() {
    initData(EMAIL, "", PHONE, "123456").run(null);

    assertThat(userRepository.existsByEmail(EMAIL)).isFalse();
  }

  // 목적: 없으면 ADMIN 으로 만들고, 비밀번호와 PIN 이 평문이 아니라 해시로 저장되는지 확인한다.
  @Test
  @DisplayName("없으면 ADMIN 으로 만들고 비밀번호와 PIN 을 해시로 저장한다")
  void createsAdminWithHashedSecrets() {
    validInitData().run(null);

    User admin = userRepository.findAllByEmail(EMAIL).get(0);
    assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(admin.getPhone()).isEqualTo(PHONE);
    assertThat(admin.getPasswordHash()).isNotEqualTo(PASSWORD);
    assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
    assertThat(admin.getPinHash()).isNotEqualTo("123456");
    assertThat(passwordEncoder.matches("123456", admin.getPinHash())).isTrue();
  }

  // 목적: 이메일에 공백·대문자가 섞여 들어와도 가입 때와 같은 방식으로 정리해 저장하는지 확인한다.
  @Test
  @DisplayName("이메일은 가입 때와 같이 공백을 지우고 소문자로 저장한다")
  void normalizesEmail() {
    initData("  Admin-Init@Test.Local ", PASSWORD, PHONE, "123456").run(null);

    assertThat(userRepository.existsByEmail(EMAIL)).isTrue();
  }

  // 목적: 서버를 다시 시작해도 이미 있는 계정의 역할과 비밀번호를 바꾸지 않고 한 건만 남는지 확인한다.
  @Test
  @DisplayName("이미 있으면 역할과 비밀번호를 덮어쓰지 않는다")
  void keepsExistingUser() {
    String existingHash = passwordEncoder.encode("Other1234!");
    userRepository.save(
        User.create("기존", EMAIL, "01011112222", existingHash, existingHash, UserRole.USER));

    validInitData().run(null);

    User user = userRepository.findAllByEmail(EMAIL).get(0);
    assertThat(user.getRole()).isEqualTo(UserRole.USER);
    assertThat(user.getPasswordHash()).isEqualTo(existingHash);
    assertThat(userRepository.count()).isEqualTo(1);
  }

  // 목적: 같은 전화번호의 다른 사용자가 있으면 시작을 실패시키지 않고 계정을 만들지 않는지 확인한다.
  @Test
  @DisplayName("같은 전화번호가 이미 있으면 건너뛴다")
  void skipsWhenPhoneExists() {
    String hash = passwordEncoder.encode("Other1234!");
    userRepository.save(User.create("기존", "other@test.local", PHONE, hash, hash, UserRole.USER));

    validInitData().run(null);

    assertThat(userRepository.existsByEmail(EMAIL)).isFalse();
    assertThat(userRepository.count()).isEqualTo(1);
  }

  // 목적: 두 번 실행해도 계정이 한 건만 남는지 확인한다.
  @Test
  @DisplayName("두 번 실행해도 한 건만 만든다")
  void idempotent() {
    validInitData().run(null);
    validInitData().run(null);

    assertThat(userRepository.count()).isEqualTo(1);
  }

  // 목적: 가입 규칙에 어긋난 값은 조용히 넘기지 않고 시작 때 바로 드러나는지, 비밀번호가 메시지에 새지 않는지 확인한다.
  @Test
  @DisplayName("가입 규칙에 어긋난 값이면 예외를 던지고 비밀번호는 메시지에 넣지 않는다")
  void rejectsInvalidValues() {
    assertThatThrownBy(() -> initData(EMAIL, "short", PHONE, "123456").run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN_PASSWORD")
        .hasMessageNotContaining("short");
    assertThatThrownBy(() -> initData(EMAIL, "alllettersonly!", PHONE, "123456").run(null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> initData(EMAIL, PASSWORD, "010-9999-0000", "123456").run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN_PHONE");
    assertThatThrownBy(() -> initData(EMAIL, PASSWORD, PHONE, "12345").run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN_PIN");
    assertThatThrownBy(() -> initData("not-an-email", PASSWORD, PHONE, "123456").run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN_EMAIL");
    assertThat(userRepository.count()).isZero();
  }
}
