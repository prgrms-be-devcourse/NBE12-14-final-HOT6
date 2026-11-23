package com.back.nbe1214finalhot6.support;

import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithAuthUserSecurityContextFactory implements WithSecurityContextFactory<WithAuthUser> {

  @Override
  public SecurityContext createSecurityContext(WithAuthUser annotation) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            new AuthUser(annotation.userId(), annotation.role()),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + annotation.role().name()))));
    return context;
  }
}
