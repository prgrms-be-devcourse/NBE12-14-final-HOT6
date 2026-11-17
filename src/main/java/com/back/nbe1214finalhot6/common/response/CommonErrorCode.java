package com.back.nbe1214finalhot6.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 공통(COM) 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
  // 요청 검증
  INVALID_REQUEST("COM-001", HttpStatus.BAD_REQUEST, "잘못된 요청입니다"),

  // 멱등키
  IDEMPOTENCY_KEY_MISSING("COM-002", HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 없습니다"),
  IDEMPOTENCY_KEY_MISMATCH(
      "COM-003", HttpStatus.CONFLICT, "같은 요청 키로 다른 내용의 요청을 보냈습니다"),
  IDEMPOTENCY_IN_PROGRESS("COM-004", HttpStatus.CONFLICT, "이전 요청을 아직 처리 중입니다"),

  // 인증·권한
  UNAUTHORIZED("COM-005", HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
  INVALID_TOKEN("COM-006", HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
  EXPIRED_TOKEN("COM-007", HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다"),
  FORBIDDEN("COM-008", HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),

  // 리소스·서버·외부 연동
  RESOURCE_NOT_FOUND("COM-009", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다"),
  INTERNAL_SERVER_ERROR("COM-010", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),
  EXTERNAL_SERVICE_FAILURE("COM-011", HttpStatus.SERVICE_UNAVAILABLE, "외부 서비스 연동에 실패했습니다"),

  // HTTP 메서드
  METHOD_NOT_ALLOWED("COM-012", HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
