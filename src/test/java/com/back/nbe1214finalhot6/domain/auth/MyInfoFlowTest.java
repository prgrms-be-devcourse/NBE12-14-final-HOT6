package com.back.nbe1214finalhot6.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 → 로그인 → 내 정보 조회를, 실제 보안 설정과 DB 로 끝까지 확인한다.
 *
 * <p>테스트 전체를 한 트랜잭션으로 감싸서(끝나면 롤백) 따로 정리하지 않는다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class MyInfoFlowTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtProvider jwtProvider;
  @Autowired UserRepository userRepository;
  @Autowired JdbcTemplate jdbcTemplate;
  @PersistenceContext EntityManager entityManager;
  @Autowired MutableClock clock;

  // 목적: 가입·로그인한 회원이 자기 정보를 조회하고, 비밀번호·PIN 같은 민감한 값은 응답에 없는지 확인한다.
  @Test
  @DisplayName("가입 → 로그인 → 내 정보 조회")
  void signupLoginMe() throws Exception {
    String token = signupAndLogin("me1@devpay.com", "01066660001");

    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("홍길동"))
        .andExpect(jsonPath("$.data.email").value("me1@devpay.com"))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.pinLocked").value(false))
        .andExpect(jsonPath("$.data.needsRelogin").value(false))
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data.pinHash").doesNotExist())
        .andExpect(jsonPath("$.data.pinFailCount").doesNotExist());
  }

  // 목적: 가맹점을 등록해 점주가 된 직후, 옛 토큰으로 /me 를 부르면 DB 의 현재 역할(OWNER)과 needsRelogin=true 를 주고,
  // 다시 로그인해 새 토큰을 받으면 needsRelogin=false 가 되는지 확인한다.
  @Test
  @DisplayName("가맹점 등록 직후 옛 토큰으로는 needsRelogin 이 true, 다시 로그인하면 false 이다")
  void needsReloginAfterPromotion() throws Exception {
    String oldToken = signupAndLogin("me2@devpay.com", "01066660002");
    assertThat(jwtProvider.parse(oldToken).role()).isEqualTo(UserRole.USER);

    mockMvc
        .perform(
            post("/api/merchants")
                .header("Authorization", "Bearer " + oldToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"내정보 가맹점\", \"address\": \"인천광역시 남동구 1\"}"))
        .andExpect(status().isCreated());

    // 토큰은 그대로(옛 역할 USER)인데, /me 는 DB 의 현재 역할과 재로그인 필요 여부를 알려 준다
    assertThat(jwtProvider.parse(oldToken).role()).isEqualTo(UserRole.USER);
    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + oldToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("OWNER"))
        .andExpect(jsonPath("$.data.needsRelogin").value(true));

    // 다시 로그인해 새 토큰(OWNER)을 받으면 재로그인은 필요 없다
    String newToken = login("/api/auth/owner/login", "me2@devpay.com");
    assertThat(jwtProvider.parse(newToken).role()).isEqualTo(UserRole.OWNER);
    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + newToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("OWNER"))
        .andExpect(jsonPath("$.data.needsRelogin").value(false));
  }

  // 목적: PIN 이 잠기면 /me 의 pinLocked 가 true 이고, 잠긴 지 10분이 지나면 자동으로 풀려 false 가 되는지
  // 실제 보안 설정과 DB 로 확인한다(임시 방식, MVP 이후 이메일 인증 해제(U7)로 대체).
  @Test
  @DisplayName("PIN 이 잠기면 pinLocked 가 true, 10분이 지나면 false 이다")
  void pinLockedFollowsTenMinuteRule() throws Exception {
    String token = signupAndLogin("me3@devpay.com", "01066660004");
    long userId = jwtProvider.parse(token).userId();
    jdbcTemplate.update(
        "update users set pin_locked_at = ? where id = ?", LocalDateTime.now(clock), userId);
    // 이 테스트는 전체가 한 트랜잭션이라 가입 때 읽은 회원 객체가 메모리에 남아 있다. SQL 로 바꾼 값을 DB 에서 다시 읽도록 비운다
    entityManager.flush();
    entityManager.clear();

    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pinLocked").value(true));

    clock.advance(Duration.ofMinutes(10));
    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pinLocked").value(false));
  }

  // 목적: 내 정보 조회는 공통 경로라 운영자(ADMIN)도 쓰고, 토큰이 없으면 401 인지 실제 보안 설정으로 확인한다.
  @Test
  @DisplayName("운영자도 조회하고, 토큰이 없으면 401 이다")
  void adminAndAnonymous() throws Exception {
    User admin =
        userRepository.save(
            User.create("관리자", "admin-me@devpay.com", "01066660003", "pw", "pin", UserRole.ADMIN));
    String adminToken = jwtProvider.createAccessToken(admin.getId(), UserRole.ADMIN);

    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("ADMIN"))
        .andExpect(jsonPath("$.data.needsRelogin").value(false));

    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COM-005"));
  }

  private String signupAndLogin(String email, String phone) throws Exception {
    signup(email, phone);
    return login(email);
  }

  private void signup(String email, String phone) throws Exception {
    mockMvc
        .perform(
            post("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "홍길동", "email": "%s", "password": "Password1!",
                     "phone": "%s", "pin": "123456"}
                    """
                        .formatted(email, phone)))
        .andExpect(status().isCreated());
  }

  private String login(String email) throws Exception {
    return login("/api/auth/login", email);
  }

  private String login(String path, String email) throws Exception {
    MvcResult login =
        mockMvc
            .perform(
                post(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\": \"%s\", \"password\": \"Password1!\"}".formatted(email)))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
  }
}
