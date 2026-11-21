package com.back.nbe1214finalhot6.domain.wallet.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 지갑(WAL) 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum WalletErrorCode implements ErrorCode {
  // 지갑 상태
  WALLET_NOT_FOUND("WAL-001", HttpStatus.NOT_FOUND, "지갑을 찾을 수 없습니다"),
  WALLET_WITHDRAWN("WAL-002", HttpStatus.CONFLICT, "탈퇴한 회원의 지갑입니다"),

  // 금액
  INVALID_AMOUNT("WAL-003", HttpStatus.BAD_REQUEST, "금액은 0보다 커야 합니다"),
  INSUFFICIENT_BALANCE("WAL-004", HttpStatus.CONFLICT, "데브머니 잔액이 부족합니다"),
  INSUFFICIENT_CASHBACK("WAL-005", HttpStatus.CONFLICT, "사용할 수 있는 캐시백이 부족합니다"),

  // 충전 한도
  HOLDING_LIMIT_EXCEEDED("WAL-006", HttpStatus.CONFLICT, "보유 한도를 초과하여 충전할 수 없습니다"),
  MONTHLY_CHARGE_LIMIT_EXCEEDED("WAL-007", HttpStatus.CONFLICT, "월 충전 한도를 초과하여 충전할 수 없습니다");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
