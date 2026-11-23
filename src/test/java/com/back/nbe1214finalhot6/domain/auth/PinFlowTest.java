package com.back.nbe1214finalhot6.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * PIN 검증을 실제 보안 설정과 DB 로 끝까지 확인한다.
 *
 * <p>실패 횟수는 새 트랜잭션으로 저장하므로 테스트 전체를 트랜잭션으로 감싸지 않고, 만든 회원만 직접 지운다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class PinFlowTest {

  private static final String PIN = "123456";

  @Autowired MockMvc mockMvc;
  @Autowired JwtProvider jwtProvider;
  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JdbcTemplate jdbcTemplate;

  private final List<Long> userIds = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    userRepository.deleteAllById(userIds);
    userIds.clear();
  }

  private User save(String email, String phone, UserRole role) {
    User user =
        userRepository.save(
            User.create("홍길동", email, phone, "pw", passwordEncoder.encode(PIN), role));
    userIds.add(user.getId());
    return user;
  }

  private ResultActions verify(User user, String pin) throws Exception {
    return mockMvc.perform(
        post("/api/users/pin/verify")
            .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getId(), user.getRole()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"pin\": \"%s\"}".formatted(pin)));
  }

  private int failCount(User user) {
    return jdbcTemplate.queryForObject(
        "select pin_fail_count from users where id = ?", Integer.class, user.getId());
  }

  // 목적: 틀릴 때마다 남은 횟수가 줄고, 5번째에 잠기며, 잠긴 뒤에는 맞는 PIN 도 거절하는지 끝까지 확인한다.
  @Test
  @DisplayName("4번 틀리면 남은 횟수 안내, 5번째에 잠기고, 잠긴 뒤에는 맞는 PIN 도 409 이다")
  void lockFlow() throws Exception {
    User user = save("pf1@devpay.com", "01044440001", UserRole.USER);

    for (int remaining = 4; remaining >= 1; remaining--) {
      verify(user, "000000")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("AUTH-005"))
          .andExpect(jsonPath("$.message").value("PIN이 일치하지 않습니다 (잔여 " + remaining + "회)"));
    }
    verify(user, "000000").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH-006"));

    verify(user, PIN).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH-006"));
    assertThat(failCount(user)).isEqualTo(5);
  }

  // 목적: 한 번 틀린 뒤 맞으면 성공하고, 실패 횟수가 0으로 돌아가 다시 5번의 기회가 생기는지 확인한다.
  @Test
  @DisplayName("틀린 뒤 맞으면 성공하고 횟수가 초기화된다")
  void resetFlow() throws Exception {
    User user = save("pf2@devpay.com", "01044440002", UserRole.USER);

    verify(user, "000000").andExpect(status().isBadRequest());
    verify(user, PIN).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    assertThat(failCount(user)).isZero();

    verify(user, "000000")
        .andExpect(jsonPath("$.message").value("PIN이 일치하지 않습니다 (잔여 4회)"));
  }

  // 목적: 형식이 틀린 요청은 PIN 시도로 세지 않는지(실패 횟수가 오르지 않는지) 확인한다.
  @Test
  @DisplayName("6자리 숫자가 아닌 요청은 400 이고 실패 횟수가 오르지 않는다")
  void formatErrorIsNotAnAttempt() throws Exception {
    User user = save("pf3@devpay.com", "01044440003", UserRole.USER);

    verify(user, "12345").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COM-001"));
    verify(user, "abcdef").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COM-001"));

    assertThat(failCount(user)).isZero();
  }

  // 목적: 점주는 쓸 수 있고, 운영자는 403, 토큰이 없으면 401 인지 실제 보안 설정으로 확인한다.
  @Test
  @DisplayName("점주는 성공하고, 운영자는 403, 토큰이 없으면 401 이다")
  void roles() throws Exception {
    User owner = save("pf4@devpay.com", "01044440004", UserRole.OWNER);
    User admin = save("pf5@devpay.com", "01044440005", UserRole.ADMIN);

    verify(owner, PIN).andExpect(status().isOk());
    verify(admin, PIN).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COM-008"));
    assertThat(failCount(admin)).isZero();

    mockMvc
        .perform(
            post("/api/users/pin/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\": \"123456\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COM-005"));
  }
}
