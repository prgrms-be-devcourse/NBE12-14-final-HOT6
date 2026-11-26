package com.back.nbe1214finalhot6.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupRequest;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupResponse;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.auth.service.AuthService;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 점주 가입이 OWNER 계정과 0원 모의계좌만 만들고, 이용자 계정과는 같은 이메일·전화번호를 쓸 수 있는지 확인한다. */
@IntegrationTest
@AutoConfigureMockMvc
class OwnerSignupFlowTest {

  private static final String EMAIL = "owner@devpay.com";
  private static final String PHONE = "01055550001";
  private static final String PASSWORD = "Password1!";

  @Autowired MockMvc mockMvc;
  @Autowired AuthService authService;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired UserRepository userRepository;
  @MockitoSpyBean AccountRepository accountRepository;
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
  @DisplayName("로그인 없이 점주로 가입하면 OWNER 계정과 0원 모의계좌가 생기고, 지갑과 계좌 원장은 없다")
  void ownerSignup() throws Exception {
    ResultActions result = postSignup("/api/owners/signup", EMAIL, PHONE, "123456");

    result.andExpect(status().isCreated()).andExpect(jsonPath("$.data.role").value("OWNER"));
    User owner = userRepository.findAllByEmail(EMAIL).get(0);
    assertThat(owner.getRole()).isEqualTo(UserRole.OWNER);
    assertThat(passwordEncoder.matches("123456", owner.getPinHash())).isTrue();
    assertThat(accountRepository.findByUserId(owner.getId()).orElseThrow().getBalance()).isZero();
    assertThat(accountTransactionRepository.count()).isZero();
    assertThat(walletRepository.findByUserId(owner.getId())).isEmpty();
  }

  @Test
  @DisplayName("점주 가입도 입력 규칙을 어기면 400 이고 아무것도 저장하지 않는다")
  void invalidInput() throws Exception {
    postSignup("/api/owners/signup", EMAIL, PHONE, "12345").andExpect(status().isBadRequest());

    assertThat(userRepository.count()).isZero();
    assertThat(accountRepository.count()).isZero();
  }

  @Test
  @DisplayName("점주 가입 중 계좌 저장이 실패하면 회원도 저장되지 않는다")
  void rollback() {
    doThrow(new IllegalStateException("계좌 저장 실패"))
        .when(accountRepository)
        .save(any(Account.class));

    assertThatThrownBy(() -> authService.signupOwner(request(EMAIL, PHONE)))
        .isInstanceOf(IllegalStateException.class);

    assertThat(userRepository.count()).isZero();
  }

  @Test
  @DisplayName("같은 이메일·전화번호로 이용자와 점주 계정을 하나씩 만들 수 있다(가입 순서와 무관)")
  void sameContactForBothRoles() {
    SignupResponse user = authService.signup(request(EMAIL, PHONE));
    SignupResponse owner = authService.signupOwner(request(EMAIL, PHONE));
    authService.signupOwner(request("second@devpay.com", "01055550002"));
    authService.signup(request("second@devpay.com", "01055550002"));

    assertThat(userRepository.count()).isEqualTo(4);
    assertThat(accountRepository.findByUserId(user.id()).orElseThrow().getBalance())
        .isEqualTo(Account.INITIAL_BALANCE);
    assertThat(accountRepository.findByUserId(owner.id()).orElseThrow().getBalance()).isZero();
  }

  @Test
  @DisplayName("점주끼리 이메일이 겹치면 AUTH-001, 전화번호가 겹치면 AUTH-002 다")
  void duplicateOwner() {
    authService.signupOwner(request(EMAIL, PHONE));

    assertError(
        () -> authService.signupOwner(request(EMAIL, "01055550002")),
        AuthErrorCode.DUPLICATE_EMAIL);
    assertError(
        () -> authService.signupOwner(request("second@devpay.com", PHONE)),
        AuthErrorCode.DUPLICATE_PHONE);
  }

  @Test
  @DisplayName("운영자와 탈퇴한 점주의 이메일·전화번호로는 점주로 가입할 수 없다")
  void reservedContacts() {
    save(EMAIL, PHONE, UserRole.ADMIN);
    User withdrawn = save("gone@devpay.com", "01055550003", UserRole.OWNER);
    jdbcTemplate.update("update users set status = 'WITHDRAWN' where id = ?", withdrawn.getId());

    assertError(
        () -> authService.signupOwner(request(EMAIL, "01055550002")),
        AuthErrorCode.DUPLICATE_EMAIL);
    assertError(
        () -> authService.signupOwner(request("second@devpay.com", PHONE)),
        AuthErrorCode.DUPLICATE_PHONE);
    assertError(
        () -> authService.signupOwner(request("gone@devpay.com", "01055550002")),
        AuthErrorCode.DUPLICATE_EMAIL);
    assertError(
        () -> authService.signupOwner(request("second@devpay.com", "01055550003")),
        AuthErrorCode.DUPLICATE_PHONE);
  }

  @Test
  @DisplayName("같은 이메일의 점주 가입이 동시에 들어와도 하나만 성공하고 나머지는 AUTH-001 이다")
  void concurrentSignup() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Object>> futures = new ArrayList<>();
      for (String phone : List.of("01055550001", "01055550002")) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  try {
                    return authService.signupOwner(request(EMAIL, phone));
                  } catch (BusinessException e) {
                    return e.getErrorCode();
                  }
                }));
      }
      start.countDown();
      List<Object> results = new ArrayList<>();
      for (Future<Object> future : futures) {
        results.add(future.get(30, TimeUnit.SECONDS));
      }

      assertThat(results).filteredOn(SignupResponse.class::isInstance).hasSize(1);
      assertThat(results).contains(AuthErrorCode.DUPLICATE_EMAIL);
      assertThat(userRepository.count()).isEqualTo(1);
      assertThat(accountRepository.count()).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  // 가입한 점주가 지갑 없이도 가맹점 기능까지 갈 수 있는지 본다. 가맹점 쪽 데이터는 롤백으로 지운다
  @Test
  @Transactional
  @DisplayName("가입한 점주는 로그인해 가맹점을 등록하고 점주 기능을 쓸 수 있다")
  void signedUpOwnerRegistersMerchant() throws Exception {
    postSignup("/api/owners/signup", EMAIL, PHONE, "123456").andExpect(status().isCreated());
    String body =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = JsonPath.read(body, "$.data.accessToken");

    mockMvc
        .perform(
            post("/api/merchants")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"새 점주 가맹점\", \"address\": \"인천광역시 남동구 1\"}"))
        .andExpect(status().isCreated());

    assertThat(userRepository.findAllByEmail(EMAIL))
        .extracting(User::getRole)
        .containsExactly(UserRole.OWNER);
  }

  private ResultActions postSignup(String path, String email, String phone, String pin)
      throws Exception {
    return mockMvc.perform(
        post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"name": "홍길동", "email": "%s", "password": "%s", "phone": "%s", "pin": "%s"}
                """
                    .formatted(email, PASSWORD, phone, pin)));
  }

  private SignupRequest request(String email, String phone) {
    return new SignupRequest("홍길동", email, PASSWORD, phone, "123456");
  }

  private User save(String email, String phone, UserRole role) {
    return userRepository.saveAndFlush(User.create("홍길동", email, phone, "pw", "pin", role));
  }

  private void assertError(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }
}
