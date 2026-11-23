package com.back.nbe1214finalhot6.domain.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginRequestTest {

  // 가입 때 저장된 이메일(공백 제거, 소문자)과 같은 값으로 찾아야 로그인이 된다
  @Test
  @DisplayName("이메일 앞뒤 공백을 지우고 소문자로 바꾼다")
  void normalizesEmail() {
    LoginRequest request = new LoginRequest("  User@Example.COM ", "Password1!");

    assertThat(request.email()).isEqualTo("user@example.com");
    assertThat(request.password()).isEqualTo("Password1!");
  }

  @Test
  @DisplayName("비밀번호는 바꾸지 않는다(공백도 그대로)")
  void keepsPasswordAsIs() {
    LoginRequest request = new LoginRequest("user@example.com", " Pass word1! ");

    assertThat(request.password()).isEqualTo(" Pass word1! ");
  }

  @Test
  @DisplayName("이메일이 null 이어도 생성할 수 있다(검증은 @NotBlank 가 한다)")
  void nullEmail() {
    assertThat(new LoginRequest(null, "Password1!").email()).isNull();
  }
}
