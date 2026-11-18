package com.back.nbe1214finalhot6.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

// SecurityConfig 없이 이 설정만으로 PasswordEncoder 빈이 만들어지는지 확인
@SpringJUnitConfig(PasswordEncoderConfig.class)
class PasswordEncoderConfigTest {

  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("SecurityConfig 없이도 BCrypt 인코더 빈이 등록된다")
  void bcryptEncoderRegistered() {
    String hash = passwordEncoder.encode("Password1!");

    assertThat(hash).startsWith("$2").isNotEqualTo("Password1!");
    assertThat(passwordEncoder.matches("Password1!", hash)).isTrue();
    assertThat(passwordEncoder.matches("wrong-password", hash)).isFalse();
  }
}
