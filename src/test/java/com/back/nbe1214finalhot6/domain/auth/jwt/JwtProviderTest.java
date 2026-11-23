package com.back.nbe1214finalhot6.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.support.MutableClock;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtProviderTest {

  private static final String SECRET = "test-secret-key-for-jwt-0123456789-abcdef";
  private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");

  private final MutableClock clock = new MutableClock(NOW, ZoneId.of("Asia/Seoul"));
  private final JwtProvider jwtProvider = new JwtProvider(new JwtProperties(SECRET, 30), clock);

  @ParameterizedTest
  @EnumSource(UserRole.class)
  @DisplayName("발급한 토큰을 해석하면 회원 ID 와 역할이 그대로 나온다")
  void roundTrip(UserRole role) {
    String token = jwtProvider.createAccessToken(42L, role);

    TokenClaims claims = jwtProvider.parse(token);

    assertThat(claims.userId()).isEqualTo(42L);
    assertThat(claims.role()).isEqualTo(role);
  }

  // 토큰은 누구나 열어볼 수 있으므로 개인정보가 들어가면 안 된다
  @Test
  @DisplayName("토큰에는 회원 ID, 역할, 발급·만료 시각만 들어 있다")
  void containsOnlyMinimalClaims() {
    String token = jwtProvider.createAccessToken(42L, UserRole.USER);

    var payload =
        Jwts.parser()
            .verifyWith(key(SECRET))
            .clock(() -> Date.from(clock.instant())) // 테스트 시계 기준으로 만료를 판단
            .build()
            .parseSignedClaims(token)
            .getPayload();

    assertThat(payload.keySet()).containsExactlyInAnyOrder("sub", "role", "iat", "exp");
    assertThat(payload.getSubject()).isEqualTo("42");
    assertThat(payload.getIssuedAt()).isEqualTo(Date.from(NOW));
    assertThat(payload.getExpiration()).isEqualTo(Date.from(NOW.plus(Duration.ofMinutes(30))));
  }

  @Test
  @DisplayName("유효 시간(초)은 설정한 분 × 60 이다")
  void expiresInSeconds() {
    assertThat(jwtProvider.accessTokenExpiresInSeconds()).isEqualTo(1800L);
  }

  @Test
  @DisplayName("만료 직전까지는 유효하고, 만료 시각이 지나면 COM-007 이다")
  void expiry() {
    String token = jwtProvider.createAccessToken(1L, UserRole.USER);

    clock.set(NOW.plus(Duration.ofMinutes(29)).plusSeconds(59));
    assertThat(jwtProvider.parse(token).userId()).isEqualTo(1L);

    clock.set(NOW.plus(Duration.ofMinutes(30)).plusSeconds(1));
    assertErrorCode(() -> jwtProvider.parse(token), CommonErrorCode.EXPIRED_TOKEN);
  }

  @Test
  @DisplayName("본문을 바꾼 토큰은 COM-006 이다")
  void tamperedPayload() {
    String valid = jwtProvider.createAccessToken(1L, UserRole.USER);
    String admin = jwtProvider.createAccessToken(1L, UserRole.ADMIN);
    // 서명은 USER 토큰의 것을 두고 본문만 ADMIN 토큰의 것으로 바꾼다
    String[] v = valid.split("\\.");
    String[] a = admin.split("\\.");
    String forged = v[0] + "." + a[1] + "." + v[2];

    assertErrorCode(() -> jwtProvider.parse(forged), CommonErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("다른 키로 서명한 토큰은 COM-006 이다")
  void differentKey() {
    JwtProvider other =
        new JwtProvider(new JwtProperties("another-secret-key-for-jwt-0123456789-xyz", 30), clock);
    String token = other.createAccessToken(1L, UserRole.ADMIN);

    assertErrorCode(() -> jwtProvider.parse(token), CommonErrorCode.INVALID_TOKEN);
  }

  // alg 를 none 으로 한 토큰으로 서명 검증을 피하려는 공격
  @Test
  @DisplayName("서명이 없는 토큰은 COM-006 이다")
  void unsignedToken() {
    String unsigned =
        Jwts.builder()
            .subject("1")
            .claim("role", "ADMIN")
            .expiration(Date.from(NOW.plus(Duration.ofMinutes(30))))
            .compact();

    assertErrorCode(() -> jwtProvider.parse(unsigned), CommonErrorCode.INVALID_TOKEN);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "not-a-token", "a.b.c", "Bearer abc"})
  @DisplayName("형식이 틀린 값은 COM-006 이다")
  void malformed(String token) {
    assertErrorCode(() -> jwtProvider.parse(token), CommonErrorCode.INVALID_TOKEN);
  }

  // 서명은 맞지만 값이 이상한 토큰도 예외가 새어 나가지 않고 COM-006 으로 거절되는지 확인
  @Test
  @DisplayName("서명은 맞아도 회원 ID 가 숫자가 아니거나 역할이 없는 값이면 COM-006 이다")
  void validSignatureButBadValues() {
    String badSubject = signed("abc", "USER");
    String badRole = signed("1", "SUPERUSER");
    String noRole = signed("1", null);

    assertErrorCode(() -> jwtProvider.parse(badSubject), CommonErrorCode.INVALID_TOKEN);
    assertErrorCode(() -> jwtProvider.parse(badRole), CommonErrorCode.INVALID_TOKEN);
    assertErrorCode(() -> jwtProvider.parse(noRole), CommonErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("서명 키가 32바이트보다 짧거나 없으면 시작할 때 실패한다")
  void weakSecretFailsFast() {
    // 실패 메시지가 무엇을 해야 하는지 알려주고, 키 값은 담지 않는지도 확인
    assertThatThrownBy(() -> new JwtProvider(new JwtProperties("too-short", 30), clock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET")
        .hasMessageContaining(".env")
        .hasMessageNotContaining("too-short");
    assertThatThrownBy(() -> new JwtProvider(new JwtProperties(null, 30), clock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
    assertThatThrownBy(() -> new JwtProvider(new JwtProperties("", 30), clock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  @DisplayName("유효 시간이 0 이하면 시작할 때 실패한다")
  void invalidValidityFailsFast() {
    assertThatThrownBy(() -> new JwtProvider(new JwtProperties(SECRET, 0), clock))
        .isInstanceOf(IllegalStateException.class);
  }

  private String signed(String subject, String role) {
    var builder =
        Jwts.builder()
            .subject(subject)
            .issuedAt(Date.from(NOW))
            .expiration(Date.from(NOW.plus(Duration.ofMinutes(30))));
    if (role != null) {
      builder.claim("role", role);
    }
    return builder.signWith(key(SECRET)).compact();
  }

  private SecretKey key(String secret) {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  private void assertErrorCode(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }
}
