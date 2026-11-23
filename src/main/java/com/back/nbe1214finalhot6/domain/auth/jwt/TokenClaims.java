package com.back.nbe1214finalhot6.domain.auth.jwt;

import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;

/** 토큰에서 꺼낸 값. 로그인할 때의 회원 ID 와 역할이다. */
public record TokenClaims(Long userId, UserRole role) {}
