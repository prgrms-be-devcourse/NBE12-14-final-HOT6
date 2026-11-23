package com.back.nbe1214finalhot6.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.jwt.TokenClaims;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 가입 → 로그인을 실제 보안 설정·DB 로 끝까지 확인한다. */
@IntegrationTest
@AutoConfigureMockMvc
class AuthLoginIntegrationTest {

  private static final String EMAIL = "login@devpay.com";
  private static final String PASSWORD = "Password1!";

  @Autowired MockMvc mockMvc;
  @Autowired JwtProvider jwtProvider;
  @Autowired MutableClock clock;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired UserRepository userRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired AccountTransactionRepository accountTransactionRepository;
  @Autowired WalletRepository walletRepository;

  @BeforeEach
  void signUp() throws Exception {
    mockMvc
        .perform(
            post("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "홍길동", "email": "%s", "password": "%s",
                     "phone": "01077778888", "pin": "123456"}
                    """
                        .formatted(EMAIL, PASSWORD)))
        .andExpect(status().isCreated());
  }

  @AfterEach
  void cleanUp() {
    accountTransactionRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("가입한 이메일·비밀번호로 로그인하면 USER 역할의 토큰을 받고, 해석하면 내 회원 ID 가 나온다")
  void login_success() throws Exception {
    MvcResult result = login(EMAIL, PASSWORD).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String body = result.getResponse().getContentAsString();
    String token = JsonPath.read(body, "$.data.accessToken");
    assertThat((String) JsonPath.read(body, "$.data.tokenType")).isEqualTo("Bearer");
    assertThat(((Number) JsonPath.read(body, "$.data.expiresIn")).longValue()).isEqualTo(1800L);

    TokenClaims claims = jwtProvider.parse(token);
    assertThat(claims.userId()).isEqualTo(userRepository.findByEmail(EMAIL).orElseThrow().getId());
    assertThat(claims.role()).isEqualTo(UserRole.USER);
    // 응답과 토큰 어디에도 해시가 없어야 한다
    assertThat(body).doesNotContain("passwordHash", "pinHash", "$2");
  }

  @Test
  @DisplayName("OWNER·ADMIN 으로 바뀐 회원은 다시 로그인하면 토큰의 역할이 바뀐다")
  void login_reflectsRoleAtLoginTime() throws Exception {
    String before = tokenOf(login(EMAIL, PASSWORD).andReturn());
    assertThat(jwtProvider.parse(before).role()).isEqualTo(UserRole.USER);

    jdbcTemplate.update("update users set role = 'OWNER' where email = ?", EMAIL);

    // 이미 발급된 토큰은 그대로(옛 역할), 다시 로그인하면 새 역할
    assertThat(jwtProvider.parse(before).role()).isEqualTo(UserRole.USER);
    String after = tokenOf(login(EMAIL, PASSWORD).andReturn());
    assertThat(jwtProvider.parse(after).role()).isEqualTo(UserRole.OWNER);

    jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", EMAIL);
    assertThat(jwtProvider.parse(tokenOf(login(EMAIL, PASSWORD).andReturn())).role())
        .isEqualTo(UserRole.ADMIN);
  }

  @Test
  @DisplayName("이메일의 대소문자·공백이 달라도 로그인된다")
  void login_normalizedEmail() throws Exception {
    login("  LOGIN@Devpay.COM ", PASSWORD).andExpect(status().isOk());
  }

  // 이메일 없음과 비밀번호 틀림은 응답이 완전히 같아야 응답만 보고 가입 여부를 알 수 없다.
  // 탈퇴한 계정도 비밀번호가 틀리면 같아야 하고, 비밀번호가 맞을 때만 탈퇴 안내(AUTH-004)가 나간다.
  @Test
  @DisplayName("이메일 없음, 비밀번호 틀림, 탈퇴+비밀번호 틀림은 같은 응답이고, 탈퇴+비밀번호 맞음만 안내가 다르다")
  void login_failuresLookIdentical() throws Exception {
    String unknownEmail = failureBody("nobody@devpay.com", PASSWORD);
    String wrongPassword = failureBody(EMAIL, "WrongPass1!");

    jdbcTemplate.update("update users set status = 'WITHDRAWN' where email = ?", EMAIL);
    String withdrawnWrongPassword = failureBody(EMAIL, "WrongPass1!");
    String withdrawnRightPassword = failureBody(EMAIL, PASSWORD);

    assertThat(unknownEmail).contains("AUTH-003");
    assertThat(wrongPassword).isEqualTo(unknownEmail);
    assertThat(withdrawnWrongPassword).isEqualTo(unknownEmail);
    assertThat(withdrawnRightPassword).contains("AUTH-004").isNotEqualTo(unknownEmail);
  }

  @Test
  @DisplayName("발급 후 유효 시간(30분)이 지나면 토큰은 만료(COM-007)된다")
  void token_expiresAfterThirtyMinutes() throws Exception {
    String token = tokenOf(login(EMAIL, PASSWORD).andReturn());

    clock.advance(Duration.ofMinutes(29));
    assertThat(jwtProvider.parse(token).role()).isEqualTo(UserRole.USER);

    clock.advance(Duration.ofMinutes(2));
    assertThatThrownBy(() -> jwtProvider.parse(token))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.EXPIRED_TOKEN));
  }

  private ResultActions login(String email, String password) throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
  }

  private String tokenOf(MvcResult result) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
  }

  private String failureBody(String email, String password) throws Exception {
    MvcResult result = login(email, password).andExpect(status().isUnauthorized()).andReturn();
    return result.getResponse().getContentAsString();
  }
}
