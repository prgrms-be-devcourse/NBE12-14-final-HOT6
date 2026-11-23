package com.back.nbe1214finalhot6.domain.auth.security;

import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;

/**
 * 인증된 요청의 사용자. 토큰에서 꺼낸 값이다.
 *
 * <p>컨트롤러에서는 {@code @AuthenticationPrincipal AuthUser user}로 받는다. 사용자는 이 값만 믿고, 요청 본문의 userId·walletId 같은
 * 값은 믿지 않는다.
 */
public record AuthUser(Long userId, UserRole role) {}
