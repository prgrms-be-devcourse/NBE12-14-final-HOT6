package com.back.nbe1214finalhot6.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 배포 환경에서 헬스 체크로 접속을 확인하기 위한 보안 설정
 * <p>TODO: 인증·권한(COM-03) 작업이 머지되면 SecurityConfig 로 통합하고 이 파일을 삭제한다.
 */
@Configuration
@Profile("prod")
public class ProdSecurityConfig {

  @Bean
  @Order(1)
  public SecurityFilterChain deployCheckFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/actuator/health/**")
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
