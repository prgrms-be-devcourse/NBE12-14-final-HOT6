package com.back.nbe1214finalhot6.domain.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWT 설정값. 서명 키와 만료 시간은 코드에 박지 않고 설정으로 받는다.
 *
 * @param secret 서명 키(HS256). 32바이트 이상이어야 한다. 운영에서는 환경 변수 JWT_SECRET 으로 넣는다
 * @param accessTokenMinutes 액세스 토큰 유효 시간(분)
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, @DefaultValue("30") long accessTokenMinutes) {

  // 레코드의 기본 toString 은 모든 필드를 찍어서, 로그나 디버거로 서명 키가 새지 않게 키를 가린다
  @Override
  public String toString() {
    return "JwtProperties[secret=****, accessTokenMinutes=" + accessTokenMinutes + "]";
  }
}
