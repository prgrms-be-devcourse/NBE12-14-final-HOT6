package com.back.nbe1214finalhot6.domain.auth.security;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.jwt.TokenClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer 토큰을 확인해서 인증 정보를 만든다.
 *
 * <p>토큰이 없으면 익명으로 그냥 통과시킨다(로그인·가입처럼 열린 경로를 위해). 토큰이 잘못됐어도 여기서 막지 않고 에러 코드만 요청에 남긴다. 인증이
 * 필요한 경로에서 {@code RestAuthenticationEntryPoint}가 그 코드로 응답하므로, 열린 경로에 만료된 토큰을 들고 와도 요청이 거절되지 않는다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  /** 토큰이 잘못됐을 때 남기는 요청 속성. 값은 {@code ErrorCode}(COM-006 또는 COM-007)다. */
  public static final String AUTH_ERROR_ATTRIBUTE = "devpay.authError";

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtProvider jwtProvider;

  public JwtAuthenticationFilter(JwtProvider jwtProvider) {
    this.jwtProvider = jwtProvider;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null) {
      authenticate(request, header);
    }
    filterChain.doFilter(request, response);
  }

  private void authenticate(HttpServletRequest request, String header) {
    if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      request.setAttribute(AUTH_ERROR_ATTRIBUTE, CommonErrorCode.INVALID_TOKEN); // Basic 등 다른 방식
      return;
    }
    try {
      TokenClaims claims = jwtProvider.parse(header.substring(BEARER_PREFIX.length()).trim());
      UsernamePasswordAuthenticationToken authentication =
          UsernamePasswordAuthenticationToken.authenticated(
              new AuthUser(claims.userId(), claims.role()),
              null,
              List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
    } catch (BusinessException e) {
      request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getErrorCode());
    }
  }
}
