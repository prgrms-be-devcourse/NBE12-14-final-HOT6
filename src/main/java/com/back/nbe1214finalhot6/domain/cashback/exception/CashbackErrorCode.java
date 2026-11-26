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
  POLICY_PERIOD_OVERLAP("CSH-002", HttpStatus.CONFLICT, "요율 적용 기간이 기존 구간과 겹칩니다"),
  INVALID_POLICY("CSH-003", HttpStatus.BAD_REQUEST, "정책 요율 또는 기간이 올바르지 않습니다"),

  // 예산
  BUDGET_NOT_FOUND("CSH-004", HttpStatus.NOT_FOUND, "해당 월의 예산을 찾을 수 없습니다"),
  BUDGET_ALLOCATION_EXCEEDED("CSH-005", HttpStatus.CONFLICT, "월 배정 합계가 연 예산을 초과합니다"),

  // 적립
  EARNING_NOT_FOUND("CSH-006", HttpStatus.NOT_FOUND, "적립 내역을 찾을 수 없습니다"),

  // 예산 금액
  INVALID_BUDGET("CSH-007", HttpStatus.BAD_REQUEST, "예산 금액이 올바르지 않습니다"),
  BUDGET_IN_USE("CSH-008", HttpStatus.CONFLICT, "예약 또는 지급이 시작된 월 예산은 수정할 수 없습니다"),

  // 요율 인하
  RATE_REDUCTION_LIMIT_EXCEEDED("CSH-009", HttpStatus.CONFLICT, "이번 달 요율 인하 횟수를 초과했습니다"),

  // 예산 이월
  TRANSFER_EXCEEDS_REMAINING("CSH-011", HttpStatus.CONFLICT, "이월 금액이 남은 예산보다 많습니다"),

  // 적립 중단
  BUDGET_ALREADY_STOPPED("CSH-012", HttpStatus.CONFLICT, "이미 중단됐거나 마감된 월 예산입니다"),

  // 공지 (CSH-013 은 지급 실행 기록이 쓴다)
  INVALID_NOTICE("CSH-014", HttpStatus.BAD_REQUEST, "공지 내용이 올바르지 않습니다"),
  NOTICE_NOT_FOUND("CSH-015", HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
