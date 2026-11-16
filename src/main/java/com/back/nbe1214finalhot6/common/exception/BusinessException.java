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
}
