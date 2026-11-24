package com.back.nbe1214finalhot6.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProperties;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경로별 허용 역할(권한 매트릭스)과 인증 실패 응답을 확인한다.
 *
 * <p>실제 컨트롤러 대신 모든 경로에 200 으로 답하는 시험용 컨트롤러를 두고, 진짜 토큰과 진짜 보안 설정으로 요청한다. 그래서 여기서 보는 것은 도메인
 * 동작이 아니라 "이 역할이 이 경로를 통과하는가"뿐이다.
 */
@WebMvcTest(controllers = SecurityConfigTest.Probe.class)
@Import({SecurityConfig.class, SecurityConfigTest.TestBeans.class, SecurityConfigTest.Probe.class})
class SecurityConfigTest {

  private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtProvider jwtProvider;
  @Autowired private MutableClock clock;

  @TestConfiguration
  static class TestBeans {
    @Bean
    MutableClock testClock() {
      return new MutableClock(NOW, ZoneId.of("Asia/Seoul"));
    }

    @Bean
    JwtProvider jwtProvider(MutableClock clock) {
      return new JwtProvider(
          new JwtProperties("security-config-test-secret-0123456789-abcdef", 30), clock);
    }
  }

  /** 모든 /api, /actuator 경로에 200 으로 답한다. 규칙에 맞는 경로만 여기까지 도달한다. */
  @RestController
  @RequestMapping({"/api/**", "/actuator/**"})
  static class Probe {
    @RequestMapping
    String ok() {
      return "ok";
    }

    @GetMapping("/api/probe/me")
    String me(@AuthenticationPrincipal AuthUser user) {
      return user.userId() + ":" + user.role();
    }
  }

  // (메서드, 경로, 익명, USER, OWNER, ADMIN 일 때의 응답 코드)
  static Stream<Arguments> matrix() {
    return Stream.of(
        // 누구나
        Arguments.of("POST", "/api/auth/login", 200, 200, 200, 200),
        Arguments.of("POST", "/api/users/signup", 200, 200, 200, 200),
        Arguments.of("GET", "/actuator/health", 200, 200, 200, 200),
        Arguments.of("GET", "/api/auth/login", 401, 200, 200, 200), // 로그인 경로는 POST 만 열려 있다
        // 운영자 전용
        Arguments.of("GET", "/api/admin/settings", 401, 403, 403, 200),
        Arguments.of("POST", "/api/admin/settlements/daily/run", 401, 403, 403, 200),
        Arguments.of("POST", "/api/admin/settlements/monthly/run", 401, 403, 403, 200),
        Arguments.of("PUT", "/api/admin/cashback/budget/annual/2026", 401, 403, 403, 200),
        Arguments.of("POST", "/api/admin/cashback/policies/reductions", 401, 403, 403, 200),
        Arguments.of("POST", "/api/admin/cashback/payouts/run", 401, 403, 403, 200),
        Arguments.of("GET", "/api/admin/cashback/policies", 401, 403, 403, 200),
        Arguments.of("POST", "/api/admin/cashback/budget/transfers", 401, 403, 403, 200),
        Arguments.of("GET", "/api/admin/settlements/reconciliation", 401, 403, 403, 200),
        Arguments.of("POST", "/api/admin/settlements/reconciliation/run", 401, 403, 403, 200),
        // 시민 기능(OWNER 포함, ADMIN 제외)
        Arguments.of("POST", "/api/payments", 401, 200, 200, 403),
        Arguments.of("GET", "/api/payments/by-key/k1", 401, 200, 200, 403),
        Arguments.of("GET", "/api/qr/abc", 401, 200, 200, 403),
        Arguments.of("GET", "/api/cashback/monthly-limit", 401, 200, 200, 403),
        Arguments.of("GET", "/api/cashback/earnings", 401, 200, 200, 403),
        Arguments.of("GET", "/api/qr/abc/status", 401, 200, 200, 403),
        Arguments.of("GET", "/api/wallet", 401, 200, 200, 403),
        Arguments.of("POST", "/api/wallet/charges", 401, 200, 200, 403),
        Arguments.of("POST", "/api/wallet/refunds", 401, 200, 200, 403),
        // 탈퇴는 시민 기능(점주는 통과하지만 서비스가 AUTH-007 로 거절, 운영자는 403)
        Arguments.of("POST", "/api/users/withdraw", 401, 200, 200, 403),
        Arguments.of("POST", "/api/users/withdraw/settle", 401, 200, 200, 403),
        // 가맹점: 등록과 이름 검색은 시민도, 관리는 점주만
        Arguments.of("POST", "/api/merchants", 401, 200, 200, 403),
        Arguments.of("GET", "/api/merchants", 401, 200, 200, 403),
        Arguments.of("GET", "/api/merchants/me", 401, 403, 200, 403),
        Arguments.of("GET", "/api/merchants/me/account", 401, 403, 200, 403),
        Arguments.of("PATCH", "/api/merchants/me", 401, 403, 200, 403),
        Arguments.of("POST", "/api/merchants/me/qr/dynamic", 401, 403, 200, 403),
        Arguments.of("GET", "/api/merchants/me/settlements/daily", 401, 403, 200, 403),
        Arguments.of("GET", "/api/merchants/me/settlements/monthly", 401, 403, 200, 403),
        // 결제 취소는 점주만
        Arguments.of("POST", "/api/payments/1/cancel", 401, 403, 200, 403),
        // 내 정보 조회는 모든 로그인 역할이 쓴다(운영자 포함)
        Arguments.of("GET", "/api/users/me", 401, 200, 200, 200),
        // PIN 검증은 이용자(OWNER 포함)만, 운영자는 못 쓴다
        Arguments.of("POST", "/api/users/pin/verify", 401, 200, 200, 403),
        // 규칙에 없는 경로는 로그인만 하면 된다(ADMIN 포함)
        Arguments.of("GET", "/api/probe/other", 401, 200, 200, 200));
  }

  @ParameterizedTest(name = "{0} {1} → 익명 {2}, USER {3}, OWNER {4}, ADMIN {5}")
  @MethodSource("matrix")
  @DisplayName("경로별 허용 역할")
  void permissionMatrix(String method, String path, int anon, int user, int owner, int admin)
      throws Exception {
    assertThat(statusOf(method, path, null)).as("익명").isEqualTo(anon);
    assertThat(statusOf(method, path, token(UserRole.USER))).as("USER").isEqualTo(user);
    assertThat(statusOf(method, path, token(UserRole.OWNER))).as("OWNER").isEqualTo(owner);
    assertThat(statusOf(method, path, token(UserRole.ADMIN))).as("ADMIN").isEqualTo(admin);
  }

  @Test
  @DisplayName("토큰이 없으면 401 과 COM-005 이다")
  void noToken() throws Exception {
    mockMvc
        .perform(build("GET", "/api/wallet", null))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("COM-005"));
  }

  @Test
  @DisplayName("만료된 토큰은 401 과 COM-007 이다")
  void expiredToken() throws Exception {
    String token = token(UserRole.USER);
    clock.advance(Duration.ofMinutes(31));

    mockMvc
        .perform(build("GET", "/api/wallet", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COM-007"));
  }

  @Test
  @DisplayName("본문을 바꾼 토큰, 형식이 틀린 값, Bearer 가 아닌 방식은 401 과 COM-006 이다")
  void invalidToken() throws Exception {
    String[] parts = token(UserRole.USER).split("\\.");
    String adminPayload = token(UserRole.ADMIN).split("\\.")[1];
    String tampered = parts[0] + "." + adminPayload + "." + parts[2]; // 서명은 USER 토큰의 것

    for (String header :
        new String[] {"Bearer " + tampered, "Bearer not-a-token", "Basic dXNlcjpwdw==", "Bearer "}) {
      mockMvc
          .perform(build("GET", "/api/wallet", header))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("COM-006"));
    }
  }

  @Test
  @DisplayName("역할이 맞지 않으면 403 과 COM-008 이다")
  void forbidden() throws Exception {
    mockMvc
        .perform(build("GET", "/api/admin/settings", "Bearer " + token(UserRole.USER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("COM-008"));
  }

  // 로그인 같은 열린 경로에 낡은 토큰을 들고 와도 거절되면 안 된다(다시 로그인하러 온 사용자)
  @Test
  @DisplayName("열린 경로는 만료되거나 잘못된 토큰이 있어도 통과한다")
  void openPathIgnoresBadToken() throws Exception {
    String expired = token(UserRole.USER);
    clock.advance(Duration.ofHours(1));

    mockMvc
        .perform(build("POST", "/api/auth/login", "Bearer " + expired))
        .andExpect(status().isOk());
    mockMvc
        .perform(build("POST", "/api/auth/login", "Bearer garbage"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Bearer 는 대소문자를 구분하지 않는다")
  void bearerIsCaseInsensitive() throws Exception {
    mockMvc
        .perform(build("GET", "/api/wallet", "bearer " + token(UserRole.USER)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("컨트롤러는 @AuthenticationPrincipal 로 토큰의 회원 ID 와 역할을 받는다")
  void currentUserIsAvailable() throws Exception {
    String token = jwtProvider.createAccessToken(42L, UserRole.OWNER);

    mockMvc
        .perform(build("GET", "/api/probe/me", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().string("42:OWNER"));
  }

  private String token(UserRole role) {
    return jwtProvider.createAccessToken(1L, role);
  }

  private int statusOf(String method, String path, String token) throws Exception {
    return mockMvc
        .perform(build(method, path, token == null ? null : "Bearer " + token))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private MockHttpServletRequestBuilder build(String method, String path, String authorization) {
    MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), path);
    if (authorization != null) {
      builder.header("Authorization", authorization);
    }
    return builder;
  }
}
