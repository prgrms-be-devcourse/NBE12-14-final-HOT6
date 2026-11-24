package com.back.nbe1214finalhot6.common.config;

import com.back.nbe1214finalhot6.common.security.RestAccessDeniedHandler;
import com.back.nbe1214finalhot6.common.security.RestAuthenticationEntryPoint;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * 인증·권한 설정(COM-03).
 *
 * <p>역할은 USER, OWNER, ADMIN 이다. OWNER 는 USER 의 기능을 포함하고(역할 계층), ADMIN 은 운영 전용이라 시민 기능을 쓰지 못한다.
 * 규칙에 없는 경로는 로그인만 하면(역할 무관) 접근한다. 새 경로를 열 때는 아래 표에 한 줄씩 추가하고, 위에서부터 먼저 맞는 규칙이 적용되므로 좁은 경로를
 * 넓은 경로보다 위에 둔다.
 */
@Configuration
public class SecurityConfig {

  /** OWNER 가 USER 의 기능도 쓸 수 있게 한다. ADMIN 은 이 계층에 넣지 않는다(운영 전용). */
  @Bean
  static RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy("ROLE_OWNER > ROLE_USER");
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtProvider jwtProvider, JsonMapper jsonMapper) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(new RestAuthenticationEntryPoint(jsonMapper))
                    .accessDeniedHandler(new RestAccessDeniedHandler(jsonMapper)))
        .authorizeHttpRequests(
            auth ->
                auth
                    // 누구나
                    .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/users/signup")
                    .permitAll()
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    // 운영자
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    // 가맹점주(결제 취소는 /api/payments/** 보다 위에 둔다)
                    .requestMatchers(HttpMethod.POST, "/api/payments/*/cancel")
                    .hasRole("OWNER")
                    // 가맹점 등록과 이름 검색은 시민도 쓴다(등록하면 OWNER 로 승격된다)
                    .requestMatchers(HttpMethod.POST, "/api/merchants")
                    .hasRole("USER")
                    .requestMatchers(HttpMethod.GET, "/api/merchants")
                    .hasRole("USER")
                    // 그 밖의 가맹점 관리(수정, QR 생성)
                    .requestMatchers("/api/merchants/**")
                    .hasRole("OWNER")
                    // 시민 기능(OWNER 도 쓴다, ADMIN 은 못 쓴다)
                    .requestMatchers(
                        "/api/cashback/**",
                        "/api/notices/**",
                        "/api/payments/**",
                        "/api/qr/**",
                        "/api/wallet/**")
                    .hasRole("USER")
                    // 내 정보 조회는 역할 구분 없이 로그인만 하면 된다(운영자 포함)
                    .requestMatchers(HttpMethod.GET, "/api/users/me")
                    .authenticated()
                    // 나머지는 로그인만 하면 된다
                    .anyRequest()
                    .authenticated());
    return http.build();
  }
}
