package com.back.nbe1214finalhot6.support;

import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * 컨트롤러 테스트에서 토큰 없이 "이 회원(AuthUser)으로 로그인한 상태"를 만든다.
 *
 * <p>{@code @WithMockUser}는 사용자가 스프링 기본 User 라서 {@code @AuthenticationPrincipal AuthUser}를 받는 컨트롤러에는 null 이 들어간다.
 * 그런 컨트롤러는 이 어노테이션을 쓴다.
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithAuthUserSecurityContextFactory.class)
public @interface WithAuthUser {

  long userId() default 1L;

  UserRole role() default UserRole.USER;
}
