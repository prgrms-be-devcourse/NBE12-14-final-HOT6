package com.back.nbe1214finalhot6.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 임시 보안 설정
 * <p>TODO: 인증·권한(COM-03) 작업이 머지되면 이 파일을 그쪽 설정으로 교체한다. 그 전까지는 가맹점 API 와 캐시백 운영자 API 만 열어 둔다.
 */
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(
                    // 경로는 한 줄에 하나씩, 알파벳순으로 추가한다.
                    "/api/admin/cashback/**",
                    "/api/admin/settings/**",
                    "/api/cashback/**",
                    "/api/auth/login",
                    "/api/merchants/**",
                    "/api/notices/**",
                    "/api/payments/**",
                    "/api/qr/**",
                    "/api/users/signup")
                .permitAll()
                .anyRequest()
                .authenticated());
    return http.build();
  }
}
