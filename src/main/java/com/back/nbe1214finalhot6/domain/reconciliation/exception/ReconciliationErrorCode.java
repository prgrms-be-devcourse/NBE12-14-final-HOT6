package com.back.nbe1214finalhot6.domain.reconciliation.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 대사(REC) 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum ReconciliationErrorCode implements ErrorCode {
  RECONCILIATION_DATE_NOT_ALLOWED(
      "REC-001", HttpStatus.BAD_REQUEST, "오늘 이후 날짜는 대사할 수 없습니다"),
  INVALID_PERIOD("REC-002", HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다 (최대 31일)");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
