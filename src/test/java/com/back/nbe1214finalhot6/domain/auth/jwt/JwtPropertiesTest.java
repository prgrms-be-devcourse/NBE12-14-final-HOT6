package com.back.nbe1214finalhot6.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

  // 로그에 객체를 그대로 찍어도 서명 키가 보이지 않아야 한다
  @Test
  @DisplayName("toString 에는 서명 키가 나오지 않는다")
  void toStringMasksSecret() {
    JwtProperties properties = new JwtProperties("super-secret-signing-key-0123456789abcd", 30);

    assertThat(properties.toString())
        .doesNotContain("super-secret-signing-key")
        .contains("secret=****")
        .contains("accessTokenMinutes=30");
  }

  @Test
  @DisplayName("키 값 자체는 그대로 읽을 수 있다(가리는 것은 출력뿐이다)")
  void secretIsStillReadable() {
    assertThat(new JwtProperties("abc", 30).secret()).isEqualTo("abc");
  }
}
