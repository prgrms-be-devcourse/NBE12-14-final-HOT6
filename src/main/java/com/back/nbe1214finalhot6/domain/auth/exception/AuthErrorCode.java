package com.back.nbe1214finalhot6.domain.auth.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 인증·회원(AUTH) 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    // 회원가입
    DUPLICATE_EMAIL("AUTH-001", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),
    DUPLICATE_PHONE("AUTH-002", HttpStatus.CONFLICT, "이미 사용 중인 핸드폰 번호입니다");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
