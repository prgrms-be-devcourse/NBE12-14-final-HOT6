package com.back.nbe1214finalhot6.domain.payment.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 결제(PAY) 에러 코드.
 *
 * <p>결제 도메인이 직접 판단하는 실패만 정의한다. 다른 도메인이 던지는 실패는 그 도메인의 코드를 그대로 쓰고,
 * T3 는 던져진 {@code BusinessException} 의 코드를 {@code payment.failure_code} 에 기록한다.
 *
 * <ul>
 *   <li>QR 만료·사용됨·없음: D 의 {@code QrErrorCode} (QR-001 ~ QR-003)
 *   <li>예산·정책: B 의 {@code CashbackErrorCode} (CSH-xxx)
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {
  // 재조회: 서버가 받은 적 없는 (walletId, 멱등키). 같은 키로 다시 결제해도 된다
  PAYMENT_NOT_FOUND("PAY-003", HttpStatus.NOT_FOUND, "결제 내역을 찾을 수 없습니다"),
  // MVP 이후(보류): PROCESSING 정리 스케줄러가 FAILED 로 바꿀 때 기록하는 코드 (docs 2-8절). 아직 던지는 곳 없음
  PAYMENT_TIMEOUT("PAY-004", HttpStatus.REQUEST_TIMEOUT, "결제 처리 시간이 초과되었습니다"),
  // MVP 이후(보류): T2 락 대기 상한(5초)을 넘었을 때 (docs 2-8절). 아직 던지는 곳 없음
  LOCK_TIMEOUT("PAY-005", HttpStatus.SERVICE_UNAVAILABLE, "요청이 많아 처리하지 못했습니다. 잠시 후 다시 시도해 주세요"),
  // 취소: 완료(COMPLETED) 상태의 결제만 취소할 수 있다
  PAYMENT_NOT_CANCELABLE("PAY-006", HttpStatus.CONFLICT, "취소할 수 없는 결제 상태입니다"),
  // 취소: 본인 가맹점의 결제만 취소할 수 있다
  PAYMENT_MERCHANT_MISMATCH("PAY-007", HttpStatus.FORBIDDEN, "본인 가맹점의 결제만 취소할 수 있습니다"),
  // 사장님 결제 내역: 시작이 끝보다 늦거나 31일을 넘는 기간
  PAYMENT_INVALID_PERIOD("PAY-008", HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다 (최대 31일)");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
