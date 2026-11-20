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
  // 금액
  //TODO : INSUFFICIENT_BALANCE 잔액부족은 지갑error코드에서 다루면 삭제예정
  PAYMENT_LIMIT_EXCEEDED("PAY-001", HttpStatus.BAD_REQUEST, "결제 한도를 초과했습니다"),
  INSUFFICIENT_BALANCE("PAY-002", HttpStatus.CONFLICT, "잔액이 부족합니다"),

  // 재조회: 서버가 받은 적 없는 (walletId, 멱등키). 같은 키로 다시 결제해도 된다
  PAYMENT_NOT_FOUND("PAY-003", HttpStatus.NOT_FOUND, "결제 내역을 찾을 수 없습니다");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
