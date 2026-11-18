package com.back.nbe1214finalhot6.domain.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignupRequestTest {

  // 이메일 정규화: 앞뒤 공백 제거와 소문자 변환
  @Test
  @DisplayName("이메일의 앞뒤 공백을 제거하고 소문자로 바꾼다")
  void email_normalized() {
    SignupRequest request = request(" User@Example.COM ");

    assertThat(request.email()).isEqualTo("user@example.com");
  }

  // 한글 입력기에서 섞일 수 있는 전각 공백도 제거
  @Test
  @DisplayName("전각 공백도 제거한다")
  void email_fullWidthSpaceStripped() {
    SignupRequest request = request("　user@example.com　");

    assertThat(request.email()).isEqualTo("user@example.com");
  }

  // 필드가 빠진 요청(null)은 예외 없이 통과시켜 검증 단계(@NotBlank)가 거절하도록 함
  @Test
  @DisplayName("이메일이 null 이어도 예외 없이 null 로 유지된다")
  void email_nullKept() {
    SignupRequest request = request(null);

    assertThat(request.email()).isNull();
  }

  // 이메일 외 필드는 변경 없음
  @Test
  @DisplayName("이메일 외 필드는 그대로 유지된다")
  void otherFields_unchanged() {
    SignupRequest request = request(" User@Example.COM ");

    assertThat(request.name()).isEqualTo("홍길동");
    assertThat(request.password()).isEqualTo("Password1!");
    assertThat(request.phone()).isEqualTo("01012345678");
    assertThat(request.pin()).isEqualTo("123456");
  }

  private SignupRequest request(String email) {
    return new SignupRequest("홍길동", email, "Password1!", "01012345678", "123456");
  }
}
