package com.back.nbe1214finalhot6.domain.qr.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum QrErrorCode implements ErrorCode {
  QR_NOT_FOUND("QR-001", HttpStatus.NOT_FOUND, "QR 코드를 찾을 수 없습니다"),
  QR_EXPIRED("QR-002", HttpStatus.GONE, "만료된 QR 코드입니다"),
  QR_ALREADY_USED("QR-003", HttpStatus.CONFLICT, "이미 사용된 QR 코드입니다"),
  QR_MISMATCH("QR-004", HttpStatus.BAD_REQUEST, "QR 코드의 가맹점 또는 금액과 일치하지 않습니다");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
