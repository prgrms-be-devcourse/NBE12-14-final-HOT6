package com.back.nbe1214finalhot6.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupRequest;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupResponse;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.entity.UserStatus;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @InjectMocks private AuthService authService;

  private final SignupRequest request =
      new SignupRequest("홍길동", "user@example.com", "Password1!", "01012345678", "123456");

  // 평문이 아니라 암호화된 값이 저장되고, 가입자는 항상 USER·ACTIVE 로 시작하는지 확인
  @Test
  @DisplayName("정상 가입이면 해시가 저장되고 USER 로 등록된다")
  void signup_success() {
    given(passwordEncoder.encode("Password1!")).willReturn("hashed-pw");
    given(passwordEncoder.encode("123456")).willReturn("hashed-pin");
    given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

    SignupResponse response = authService.signup(request);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();

    assertThat(saved.getPasswordHash()).isEqualTo("hashed-pw").isNotEqualTo("Password1!");
    assertThat(saved.getPinHash()).isEqualTo("hashed-pin").isNotEqualTo("123456");
    assertThat(saved.getName()).isEqualTo("홍길동");
    assertThat(saved.getEmail()).isEqualTo("user@example.com");
    assertThat(saved.getPhone()).isEqualTo("01012345678");
    assertThat(saved.getRole()).isEqualTo(UserRole.USER);
    assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(saved.getPinFailCount()).isZero();
    assertThat(saved.getPinLockedAt()).isNull();

    assertThat(response.name()).isEqualTo("홍길동");
    assertThat(response.email()).isEqualTo("user@example.com");
    assertThat(response.role()).isEqualTo(UserRole.USER);
  }

  // 이메일이 중복이면 DUPLICATE_EMAIL 로 거절, 암호화나 저장까지 가지 않는지 확인
  @Test
  @DisplayName("이미 등록된 이메일이면 DUPLICATE_EMAIL 예외가 나고 저장되지 않는다")
  void signup_duplicateEmail() {
    given(userRepository.existsByEmail("user@example.com")).willReturn(true);

    assertThatThrownBy(() -> authService.signup(request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.DUPLICATE_EMAIL));

    verify(userRepository, never()).save(any());
    verifyNoInteractions(passwordEncoder);
  }

  // 전화번호가 중복이면 DUPLICATE_PHONE 으로 거절, 암호화나 저장까지 가지 않는지 확인
  @Test
  @DisplayName("이미 등록된 전화번호면 DUPLICATE_PHONE 예외가 나고 저장되지 않는다")
  void signup_duplicatePhone() {
    given(userRepository.existsByPhone("01012345678")).willReturn(true);

    assertThatThrownBy(() -> authService.signup(request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.DUPLICATE_PHONE));

    verify(userRepository, never()).save(any());
    verifyNoInteractions(passwordEncoder);
  }
}
