package com.back.nbe1214finalhot6.common.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  // 기본 메시지 대신 상황별 메시지가 필요할 때 (예: "PIN이 일치하지 않습니다 (잔여 3회)")
  public BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
