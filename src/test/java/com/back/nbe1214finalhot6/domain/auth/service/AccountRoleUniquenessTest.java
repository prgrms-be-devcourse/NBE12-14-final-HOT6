package com.back.nbe1214finalhot6.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.auth.dto.LoginRequest;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupRequest;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 이용자와 점주가 같은 이메일·전화번호를 따로 쓸 수 있고, 같은 계정 종류끼리만 중복이 막히는지 확인한다. */
@IntegrationTest
class AccountRoleUniquenessTest {

  private static final String EMAIL = "same@devpay.com";
  private static final String PHONE = "01012345678";
  private static final String USER_PASSWORD = "User1234!";
  private static final String OWNER_PASSWORD = "Owner123!";

  @Autowired AuthService authService;
  @Autowired UserRoleService userRoleService;
  @Autowired JwtProvider jwtProvider;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired UserRepository userRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired AccountTransactionRepository accountTransactionRepository;
  @Autowired WalletRepository walletRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    accountTransactionRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("점주 계정이 쓰는 이메일·전화번호로도 이용자 가입이 된다")
  void userSignupWithOwnerContact() {
    save(EMAIL, PHONE, UserRole.OWNER, OWNER_PASSWORD);

    authService.signup(request(EMAIL, PHONE));

    assertThat(userRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("운영자의 이메일·전화번호로는 이용자로 가입할 수 없다")
  void adminContactIsReserved() {
    save(EMAIL, PHONE, UserRole.ADMIN, USER_PASSWORD);

    assertError(
        () -> authService.signup(request(EMAIL, "01011112222")), AuthErrorCode.DUPLICATE_EMAIL);
    assertError(
        () -> authService.signup(request("other@devpay.com", PHONE)),
        AuthErrorCode.DUPLICATE_PHONE);
  }

  @Test
  @DisplayName("탈퇴한 이용자의 이메일·전화번호로는 아직 다시 가입할 수 없다")
  void withdrawnUserStillBlocked() {
    Long id = authService.signup(request(EMAIL, PHONE)).id();
    withdraw(id);

    assertError(
        () -> authService.signup(request(EMAIL, "01011112222")), AuthErrorCode.DUPLICATE_EMAIL);
    assertError(
        () -> authService.signup(request("other@devpay.com", PHONE)),
        AuthErrorCode.DUPLICATE_PHONE);
  }

  // 사전 확인을 거치지 않고 저장해도 DB 의 부분 유니크 인덱스가 막는지 확인한다
  @Test
  @DisplayName("DB 는 활동 중인 계정의 (이메일, 역할), (전화번호, 역할) 중복만 막는다")
  void partialUniqueIndex() {
    User first = save(EMAIL, PHONE, UserRole.USER, USER_PASSWORD);

    assertThatThrownBy(() -> save(EMAIL, "01011112222", UserRole.USER, USER_PASSWORD))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_users_email_role_active");
    assertThatThrownBy(() -> save("other@devpay.com", PHONE, UserRole.USER, USER_PASSWORD))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_users_phone_role_active");

    save(EMAIL, PHONE, UserRole.OWNER, OWNER_PASSWORD); // 역할이 다르면 허용
    withdraw(first.getId());
    save(EMAIL, PHONE, UserRole.USER, USER_PASSWORD); // 탈퇴한 계정과 겹치는 것은 DB 가 허용(재가입 허용 여부는 서비스가 정한다)

    assertThat(userRepository.count()).isEqualTo(3);
  }

  @Test
  @DisplayName("이메일과 비밀번호가 같아도 로그인은 이용자 계정, 점주 로그인은 점주 계정이다")
  void loginPicksAccountByEndpoint() {
    User owner = save(EMAIL, PHONE, UserRole.OWNER, USER_PASSWORD);
    Long userId = authService.signup(request(EMAIL, PHONE)).id();

    assertThat(loginId(USER_PASSWORD)).isEqualTo(userId);
    assertThat(ownerLoginId(USER_PASSWORD)).isEqualTo(owner.getId());
  }

  @Test
  @DisplayName("점주 비밀번호로 하는 기존 로그인과 이용자 비밀번호로 하는 점주 로그인은 LOGIN_FAILED 다")
  void otherKindOfAccountIsRejected() {
    save(EMAIL, PHONE, UserRole.OWNER, OWNER_PASSWORD);
    authService.signup(request(EMAIL, PHONE));

    assertError(
        () -> authService.login(new LoginRequest(EMAIL, OWNER_PASSWORD)),
        AuthErrorCode.LOGIN_FAILED);
    assertError(
        () -> authService.loginOwner(new LoginRequest(EMAIL, USER_PASSWORD)),
        AuthErrorCode.LOGIN_FAILED);
  }

  @Test
  @DisplayName("운영자는 기존 로그인만 쓸 수 있다")
  void adminUsesCommonLogin() {
    User admin = save(EMAIL, PHONE, UserRole.ADMIN, USER_PASSWORD);

    assertThat(loginId(USER_PASSWORD)).isEqualTo(admin.getId());
    assertError(
        () -> authService.loginOwner(new LoginRequest(EMAIL, USER_PASSWORD)),
        AuthErrorCode.LOGIN_FAILED);
  }

  @Test
  @DisplayName("같은 종류에 탈퇴 계정과 활동 계정이 함께 있으면 활동 계정으로 로그인된다")
  void loginPrefersActiveAccount() {
    User old = save(EMAIL, PHONE, UserRole.OWNER, OWNER_PASSWORD);
    withdraw(old.getId());
    User current = save(EMAIL, PHONE, UserRole.OWNER, OWNER_PASSWORD);

    assertThat(ownerLoginId(OWNER_PASSWORD)).isEqualTo(current.getId());
  }

  @Test
  @DisplayName("탈퇴한 이용자는 같은 이메일의 점주 계정이 있어도 이용자 로그인에서 AUTH-004 를 받는다")
  void withdrawnUserWithActiveOwner() {
    Long userId = authService.signup(request(EMAIL, PHONE)).id();
    withdraw(userId);
    User owner = save(EMAIL, PHONE, UserRole.OWNER, USER_PASSWORD);

    assertError(
        () -> authService.login(new LoginRequest(EMAIL, USER_PASSWORD)),
        AuthErrorCode.WITHDRAWN_USER);
    assertThat(ownerLoginId(USER_PASSWORD)).isEqualTo(owner.getId());
  }

  @Test
  @DisplayName("같은 이메일의 점주 계정이 있으면 이용자를 점주로 올리지 않고 AUTH-001 로 거절한다")
  void promotionBlockedByEmail() {
    save(EMAIL, "01011112222", UserRole.OWNER, OWNER_PASSWORD);
    Long userId = authService.signup(request(EMAIL, PHONE)).id();

    assertError(() -> userRoleService.promoteToOwner(userId), AuthErrorCode.DUPLICATE_EMAIL);

    assertThat(userRepository.findById(userId).orElseThrow().getRole()).isEqualTo(UserRole.USER);
  }

  @Test
  @DisplayName("같은 전화번호의 점주 계정이 있으면 승격을 AUTH-002 로 거절한다")
  void promotionBlockedByPhone() {
    save("owner@devpay.com", PHONE, UserRole.OWNER, OWNER_PASSWORD);
    Long userId = authService.signup(request(EMAIL, PHONE)).id();

    assertError(() -> userRoleService.promoteToOwner(userId), AuthErrorCode.DUPLICATE_PHONE);
  }

  private SignupRequest request(String email, String phone) {
    return new SignupRequest("홍길동", email, USER_PASSWORD, phone, "123456");
  }

  private User save(String email, String phone, UserRole role, String password) {
    return userRepository.saveAndFlush(
        User.create("홍길동", email, phone, passwordEncoder.encode(password), "pin", role));
  }

  private void withdraw(Long userId) {
    jdbcTemplate.update("update users set status = 'WITHDRAWN' where id = ?", userId);
  }

  private Long ownerLoginId(String password) {
    return jwtProvider
        .parse(authService.loginOwner(new LoginRequest(EMAIL, password)).accessToken())
        .userId();
  }

  private Long loginId(String password) {
    return jwtProvider.parse(authService.login(new LoginRequest(EMAIL, password)).accessToken()).userId();
  }

  private void assertError(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }
}
