package com.back.nbe1214finalhot6.domain.merchant.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MerchantErrorCode implements ErrorCode {
  MERCHANT_NOT_FOUND("MER-001", HttpStatus.NOT_FOUND, "가맹점을 찾을 수 없습니다");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
