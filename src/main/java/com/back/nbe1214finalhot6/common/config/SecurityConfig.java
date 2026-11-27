package com.back.nbe1214finalhot6.common.config;

import com.back.nbe1214finalhot6.common.security.RestAccessDeniedHandler;
import com.back.nbe1214finalhot6.common.security.RestAuthenticationEntryPoint;
import com.back.nbe1214finalhot6.domain.auth.entity.UserStatus;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.auth.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import java.util.function.LongPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * 인증·권한 설정(COM-03).
 *
 * <p>역할은 USER(이용자), OWNER(점주), ADMIN(운영자)이고 서로의 기능을 포함하지 않는다. 둘 이상의 역할이 쓰는 경로는 규칙에 함께 적는다.
 * 규칙에 없는 경로는 로그인만 하면(역할 무관) 접근한다. 새 경로를 열 때는 아래 표에 한 줄씩 추가하고, 위에서부터 먼저 맞는 규칙이 적용되므로 좁은 경로를
 * 넓은 경로보다 위에 둔다.
 */
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtProvider jwtProvider,
      JsonMapper jsonMapper,
      ObjectProvider<UserRepository> userRepository)
      throws Exception {
    // 회원 저장소가 없는 환경(컨트롤러 단위 테스트)에서는 탈퇴 확인을 건너뛴다
    UserRepository users = userRepository.getIfAvailable();
    LongPredicate isWithdrawn =
        users == null ? id -> false : id -> users.existsByIdAndStatus(id, UserStatus.WITHDRAWN);
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtProvider, isWithdrawn),
            UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(new RestAuthenticationEntryPoint(jsonMapper))
                    .accessDeniedHandler(new RestAccessDeniedHandler(jsonMapper)))
        .authorizeHttpRequests(
            auth ->
                auth
                    // 비동기 응답(SSE)의 마무리 처리. 처음 요청에서 이미 권한을 검사했고, 이 단계에는 토큰 필터가 다시 돌지 않는다
                    .dispatcherTypeMatchers(DispatcherType.ASYNC)
                    .permitAll()
                    // 누구나
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/login",
                        "/api/auth/owner/login",
                        "/api/users/signup",
                        "/api/owners/signup")
                    .permitAll()
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    // API 명세(Swagger UI). 운영 환경에서는 springdoc 을 꺼 두어 이 경로가 열리지 않는다
                    .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    // 운영자
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    // 가맹점주(결제 취소는 /api/payments/** 보다 위에 둔다)
                    .requestMatchers(HttpMethod.POST, "/api/payments/*/cancel")
                    .hasRole("OWNER")
                    // 가맹점 이름 검색은 이용자만
                    .requestMatchers(HttpMethod.GET, "/api/merchants")
                    .hasRole("USER")
                    // 가맹점 등록과 관리(수정, QR 생성)는 점주만
                    .requestMatchers("/api/merchants/**")
                    .hasRole("OWNER")
                    // 점주 화면도 쓰는 QR 한도·상태 조회(/api/qr/** 보다 위에 둔다)
                    .requestMatchers(HttpMethod.GET, "/api/qr/limit", "/api/qr/*/status")
                    .hasAnyRole("USER", "OWNER")
                    // 이용자 기능
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
                    // PIN 검증은 이용자와 점주가 쓴다. 운영자는 못 쓴다
                    .requestMatchers(HttpMethod.POST, "/api/users/pin/verify")
                    .hasAnyRole("USER", "OWNER")
                    // 탈퇴는 이용자만 요청할 수 있다. 점주 탈퇴는 아직 지원하지 않는다
                    .requestMatchers(
                        HttpMethod.POST, "/api/users/withdraw", "/api/users/withdraw/settle")
                    .hasRole("USER")
                    // 나머지는 로그인만 하면 된다
                    .anyRequest()
                    .authenticated());
    return http.build();
  }
}
