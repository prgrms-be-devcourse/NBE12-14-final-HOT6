package com.back.nbe1214finalhot6.domain.wallet.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 가맹점 지갑(MWL) 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum MerchantWalletErrorCode implements ErrorCode {
  MERCHANT_WALLET_NOT_FOUND("MWL-001", HttpStatus.NOT_FOUND, "가맹점 지갑을 찾을 수 없습니다"),
  INVALID_AMOUNT("MWL-002", HttpStatus.BAD_REQUEST, "금액은 0보다 커야 합니다"),
  INSUFFICIENT_BALANCE("MWL-003", HttpStatus.CONFLICT, "가맹점 지갑 잔액이 부족합니다"),
  MERCHANT_ACCOUNT_NOT_FOUND("MWL-004", HttpStatus.NOT_FOUND, "가맹점 계좌를 찾을 수 없습니다"),
  AMOUNT_MISMATCH("MWL-005", HttpStatus.CONFLICT, "이미 반영된 정산 건과 금액이 다릅니다");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
