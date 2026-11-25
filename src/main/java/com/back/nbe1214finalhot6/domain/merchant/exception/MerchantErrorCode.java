package com.back.nbe1214finalhot6.domain.merchant.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MerchantErrorCode implements ErrorCode {
  MERCHANT_NOT_FOUND("MER-001", HttpStatus.NOT_FOUND, "가맹점을 찾을 수 없습니다"),
  MERCHANT_ALREADY_REGISTERED("MER-002", HttpStatus.CONFLICT, "이미 가맹점을 등록했습니다. 점주 한 명당 가맹점은 하나만 등록할 수 있습니다"),
  MERCHANT_CLOSING("MER-003", HttpStatus.CONFLICT, "해지 신청이 접수된 가맹점이라 사용할 수 없습니다"),
  MERCHANT_CLOSED("MER-004", HttpStatus.CONFLICT, "해지된 가맹점입니다"),
  MERCHANT_CLOSURE_SHORTFALL(
      "MER-005", HttpStatus.CONFLICT, "취소·환수로 정산금보다 갚을 금액이 많아 해지를 신청할 수 없습니다. 부족한 금액을 먼저 정리해 주세요");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
