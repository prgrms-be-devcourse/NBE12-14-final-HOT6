package com.back.nbe1214finalhot6.domain.auth.jwt;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰 발급과 해석.
 *
 * <p>토큰에는 회원 ID(subject)와 역할, 발급·만료 시각만 넣는다. 이름·이메일 같은 개인정보는 넣지 않는다. 해석에 실패하면 만료는
 * COM-007, 그 밖의 모든 경우(위조, 형식 오류, 서명 없음)는 COM-006 으로 구분해 던진다.
 */
@Component
public class JwtProvider {

  private static final String ROLE_CLAIM = "role";
  private static final int MIN_SECRET_BYTES = 32; // HS256 의 최소 키 길이(256비트)

  private final SecretKey key;
  private final Duration accessTokenValidity;
  private final Clock clock;
  private final JwtParser parser; // 요청마다 새로 만들지 않고 한 번 만들어 재사용한다(스레드 안전)

  public JwtProvider(JwtProperties properties, Clock clock) {
    String secret = properties.secret();
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      // 키 값은 메시지에 넣지 않는다. 시작이 실패했을 때 무엇을 해야 하는지만 알려준다
      throw new IllegalStateException(
          "JWT 서명 키가 없거나 32바이트보다 짧습니다. 환경 변수 JWT_SECRET 을 설정하세요 "
              + "(로컬은 프로젝트 폴더의 .env 에 JWT_SECRET=키 한 줄, README 참고)");
    }
    if (properties.accessTokenMinutes() <= 0) {
      throw new IllegalStateException("jwt.access-token-minutes 는 0보다 커야 합니다");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenValidity = Duration.ofMinutes(properties.accessTokenMinutes());
    this.clock = clock;
    this.parser =
        Jwts.parser()
            .verifyWith(key)
            .clock(() -> Date.from(clock.instant())) // 만료 판단도 같은 시계를 쓴다
            .build();
  }

  public String createAccessToken(Long userId, UserRole role) {
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim(ROLE_CLAIM, role.name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(accessTokenValidity)))
        .signWith(key)
        .compact();
  }

  /** 액세스 토큰의 유효 시간(초). 로그인 응답의 expiresIn 이다. */
  public long accessTokenExpiresInSeconds() {
    return accessTokenValidity.toSeconds();
  }

  public TokenClaims parse(String token) {
    if (token == null || token.isBlank()) {
      throw new BusinessException(CommonErrorCode.INVALID_TOKEN);
    }
    try {
      Claims claims = parser.parseSignedClaims(token).getPayload();
      String role = claims.get(ROLE_CLAIM, String.class);
      if (role == null) { // 서명은 맞아도 역할이 없는 토큰은 받지 않는다
        throw new BusinessException(CommonErrorCode.INVALID_TOKEN);
      }
      return new TokenClaims(Long.valueOf(claims.getSubject()), UserRole.valueOf(role));
    } catch (ExpiredJwtException e) {
      throw new BusinessException(CommonErrorCode.EXPIRED_TOKEN);
    } catch (JwtException | IllegalArgumentException e) {
      // 서명 불일치, 형식 오류, 서명 없는 토큰, subject·role 값 이상(NumberFormat 도 IllegalArgument)
      throw new BusinessException(CommonErrorCode.INVALID_TOKEN);
    }
  }
}
