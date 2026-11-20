package com.back.nbe1214finalhot6.domain.payment.dto;

import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 응답 (영수증 기본 필드 포함).
 *
 * <p>결제가 끝나기 전(PROCESSING)에는 사용분·적립 관련 값이 아직 없으므로 0 또는 null 이다.
 * TODO: 월 한도 상태(남은 한도)는 B(캐시백)와 별도 조회로 합의한 뒤 추가한다.
 */
public record PaymentResponse(
    Long paymentId,
    PaymentStatus status,
    Long amount, // 결제금액
    Long walletUsedAmount, // 데브머니 사용분
    Long cashbackUsedAmount, // 캐시백 사용분
    Long cashbackEarnedAmount, // 적립 캐시백 (소수점 이하 버림, 적립 없으면 0 또는 null)
    BigDecimal appliedRate, // 적용 요율 (적립 정책이 없으면 null)
    LocalDateTime cashbackPayableAt, // 캐시백 지급 예정 시각 (적립 없으면 null)
    String cashbackExclusionReason, // 적립 제외 사유 (적립됐으면 NONE, 결제 전이면 null)
    String failureCode // 결제실패코드
    ) {

  public static PaymentResponse of(Payment payment) {
    return new PaymentResponse(
        payment.getId(),
        payment.getStatus(),
        payment.getAmount(),
        payment.getWalletUsedAmount(),
        payment.getCashbackUsedAmount(),
        payment.getCashbackEarnedAmount(),
        payment.getAppliedRate(),
        payment.getCashbackPayableAt(),
        payment.getCashbackExclusionReason(),
        payment.getFailureCode()
    );
  }
}
