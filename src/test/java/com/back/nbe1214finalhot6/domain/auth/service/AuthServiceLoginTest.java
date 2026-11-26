package com.back.nbe1214finalhot6.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.dto.LoginRequest;
import com.back.nbe1214finalhot6.domain.auth.dto.LoginResponse;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.entity.UserStatus;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletOpeningService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private WalletOpeningService walletOpeningService;
  @Mock private JwtProvider jwtProvider;
  @InjectMocks private AuthService authService;

  private final LoginRequest request = new LoginRequest("user@example.com", "Password1!");

  @Test
  @DisplayName("이메일과 비밀번호가 맞으면 회원 ID 와 역할로 토큰을 발급한다")
  void login_success() {
    User user = user(7L, UserRole.OWNER, UserStatus.ACTIVE);
    given(userRepository.findAllByEmail("user@example.com")).willReturn(List.of(user));
    given(passwordEncoder.matches("Password1!", "hashed-pw")).willReturn(true);
    given(jwtProvider.createAccessToken(7L, UserRole.OWNER)).willReturn("access-token");
    given(jwtProvider.accessTokenExpiresInSeconds()).willReturn(1800L);

    LoginResponse response = authService.login(request);

    assertThat(response.accessToken()).isEqualTo("access-token");
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.expiresIn()).isEqualTo(1800L);
  }

  // 없는 이메일이어도 비밀번호 비교를 하는지 확인(비교 시간으로 가입 여부를 알아내지 못하게 하려는 것)
  @Test
  @DisplayName("없는 이메일이면 LOGIN_FAILED 이고, 그래도 비밀번호 비교는 한 번 한다")
  void login_unknownEmail() {
    given(userRepository.findAllByEmail("user@example.com")).willReturn(List.of());

    assertLoginFailed(request);

    verify(passwordEncoder).matches(eq("Password1!"), anyString());
    verify(jwtProvider, never()).createAccessToken(any(), any());
  }

  @Test
  @DisplayName("비밀번호가 틀리면 LOGIN_FAILED 이고 토큰을 발급하지 않는다")
  void login_wrongPassword() {
    User user = user(7L, UserRole.USER, UserStatus.ACTIVE);
    given(userRepository.findAllByEmail("user@example.com")).willReturn(List.of(user));
    given(passwordEncoder.matches("Password1!", "hashed-pw")).willReturn(false);

    assertLoginFailed(request);

    verify(jwtProvider, never()).createAccessToken(any(), any());
  }

  // 비밀번호가 맞은 본인에게만 탈퇴 안내를 하는지 확인
  @Test
  @DisplayName("탈퇴한 회원이 비밀번호까지 맞으면 WITHDRAWN_USER 로 안내한다")
  void login_withdrawnUser() {
    User user = user(7L, UserRole.USER, UserStatus.WITHDRAWN);
    given(userRepository.findAllByEmail("user@example.com")).willReturn(List.of(user));
    given(passwordEncoder.matches("Password1!", "hashed-pw")).willReturn(true);

    assertLoginFailed(request, AuthErrorCode.WITHDRAWN_USER);

    verify(jwtProvider, never()).createAccessToken(any(), any());
  }

  // 비밀번호를 모르는 사람이 탈퇴 여부를 알아내지 못하도록, 비밀번호가 틀리면 탈퇴 계정도 같은 에러다
  @Test
  @DisplayName("탈퇴한 회원이어도 비밀번호가 틀리면 LOGIN_FAILED 이다(탈퇴 여부를 알려 주지 않는다)")
  void login_withdrawnUserWithWrongPassword() {
    User user = user(7L, UserRole.USER, UserStatus.WITHDRAWN);
    given(userRepository.findAllByEmail("user@example.com")).willReturn(List.of(user));
    given(passwordEncoder.matches("Password1!", "hashed-pw")).willReturn(false);

    assertLoginFailed(request);

    verify(jwtProvider, never()).createAccessToken(any(), any());
  }

  private void assertLoginFailed(LoginRequest loginRequest) {
    assertLoginFailed(loginRequest, AuthErrorCode.LOGIN_FAILED);
  }

  private void assertLoginFailed(LoginRequest loginRequest, AuthErrorCode expected) {
    assertThatThrownBy(() -> authService.login(loginRequest))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }

  private User user(Long id, UserRole role, UserStatus status) {
    User user = User.create("홍길동", "user@example.com", "01012345678", "hashed-pw", "hashed-pin", role);
    ReflectionTestUtils.setField(user, "id", id);
    ReflectionTestUtils.setField(user, "status", status);
    return user;
  }
}
