package com.back.nbe1214finalhot6.common.response;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 공통 규약. 코드 형식은 {도메인}-{3자리 번호} (예: COM-001, PAY-003).
 *
 * <p>도메인별 enum 이 이 인터페이스를 구현한다. (COM → CommonErrorCode, PAY → PaymentErrorCode ...)
 */
public interface ErrorCode {

  String getCode();

  HttpStatus getStatus();

  String getMessage();
}
