package com.back.nbe1214finalhot6.domain.auth.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 인증·회원(AUTH) 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
  // 회원가입
  DUPLICATE_EMAIL("AUTH-001", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),
  DUPLICATE_PHONE("AUTH-002", HttpStatus.CONFLICT, "이미 사용 중인 전화번호입니다"),

  // 로그인. 이메일 없음·비밀번호 불일치·탈퇴 회원을 구분하지 않고 같은 응답을 준다(회원 존재 여부 노출 방지)
  LOGIN_FAILED("AUTH-003", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
