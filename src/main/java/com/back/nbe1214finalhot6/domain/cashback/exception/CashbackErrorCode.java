package com.back.nbe1214finalhot6.domain.cashback.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 캐시백(CSH) 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum CashbackErrorCode implements ErrorCode {
  // 정책
  POLICY_NOT_FOUND("CSH-001", HttpStatus.NOT_FOUND, "캐시백 정책을 찾을 수 없습니다"),
  POLICY_PERIOD_OVERLAP("CSH-002", HttpStatus.CONFLICT, "같은 유형의 정책 기간이 겹칩니다"),
  INVALID_POLICY("CSH-003", HttpStatus.BAD_REQUEST, "정책 요율 또는 기간이 올바르지 않습니다"),

  // 예산
  BUDGET_NOT_FOUND("CSH-004", HttpStatus.NOT_FOUND, "해당 월의 예산을 찾을 수 없습니다"),
  BUDGET_ALLOCATION_EXCEEDED("CSH-005", HttpStatus.CONFLICT, "월 배정 합계가 연 예산을 초과합니다"),

  // 적립
  EARNING_NOT_FOUND("CSH-006", HttpStatus.NOT_FOUND, "적립 내역을 찾을 수 없습니다");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
