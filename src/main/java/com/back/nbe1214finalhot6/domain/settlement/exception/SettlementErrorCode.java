package com.back.nbe1214finalhot6.domain.settlement.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 정산(STL) 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements ErrorCode {
  SETTLEMENT_DATE_NOT_ALLOWED("STL-001", HttpStatus.BAD_REQUEST, "오늘 이후 날짜는 정산할 수 없습니다"),
  INVALID_PERIOD("STL-002", HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다 (최대 31일)"),
  SETTLEMENT_MONTH_NOT_ALLOWED(
      "STL-003", HttpStatus.BAD_REQUEST, "이번 달 이후는 월 정산할 수 없습니다"),
  DAILY_SETTLEMENT_INCOMPLETE(
      "STL-005", HttpStatus.CONFLICT, "해당 월의 일 정산이 끝나지 않았습니다. 일 정산을 마친 뒤 다시 실행해 주세요");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
