package com.back.nbe1214finalhot6.domain.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 비밀번호·PIN 해시용 인코더. 보안 필터 설정(SecurityConfig)을 교체해도 유지되도록 별도 설정으로 분리 */
@Configuration
public class PasswordEncoderConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
