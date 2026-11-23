package com.back.nbe1214finalhot6.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** 컨트롤러 테스트가 쓸 {@link WithAuthUser}가 토큰 없이 AuthUser 로그인 상태를 만드는지. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {})
class WithAuthUserTest {

  // 목적: 기본값(회원 1번, USER)으로 AuthUser 가 로그인한 상태가 되고 권한도 역할 이름으로 붙는지 확인한다.
  @Test
  @WithAuthUser
  @DisplayName("기본값은 1번 회원, USER 역할이다")
  void defaults() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    assertThat(auth.getPrincipal()).isEqualTo(new AuthUser(1L, UserRole.USER));
    assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
  }

  // 목적: 회원 번호와 역할을 지정하면 그대로 principal 에 들어가 @AuthenticationPrincipal 로 받을 수 있는지 확인한다.
  @Test
  @WithAuthUser(userId = 7L, role = UserRole.OWNER)
  @DisplayName("회원 번호와 역할을 지정할 수 있다")
  void custom() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    assertThat(auth.getPrincipal()).isEqualTo(new AuthUser(7L, UserRole.OWNER));
    assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_OWNER");
  }
}
