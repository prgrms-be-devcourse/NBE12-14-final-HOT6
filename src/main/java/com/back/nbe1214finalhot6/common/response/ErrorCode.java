package com.back.nbe1214finalhot6.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  // 공통
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다"),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 경로입니다"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다"),
  CONCURRENT_UPDATE(HttpStatus.CONFLICT, "다른 요청과 충돌했습니다. 다시 시도해주세요"),
  DATA_CONFLICT(HttpStatus.CONFLICT, "요청이 기존 데이터와 충돌합니다"),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다"),
  // 멱등키
  IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "멱등키가 없거나 형식이 올바르지 않습니다"),
  IDEMPOTENCY_KEY_MISMATCH(HttpStatus.CONFLICT, "같은 멱등키로 다른 내용을 요청할 수 없습니다"),
  PAYMENT_PROCESSING(HttpStatus.CONFLICT, "결제 처리 중입니다"),
  // 결제
  INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "잔액이 부족합니다"),
  QR_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 QR입니다"),
  QR_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용된 QR입니다"),
  CASHBACK_NEGATIVE(HttpStatus.BAD_REQUEST, "캐시백이 음수여서 사용할 수 없습니다"),
  PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다"),
  ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 결제입니다");

  private final HttpStatus status;
  private final String message;
}
