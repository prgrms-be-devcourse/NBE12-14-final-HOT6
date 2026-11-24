package com.back.nbe1214finalhot6.domain.payment.dto;

import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;

/**
 * 사장님 결제 내역의 결제 한 건. 결제한 이용자의 정보(지갑 번호 등)는 담지 않는다.
 *
 * @param paymentId 결제 번호 (취소할 때 쓴다)
 * @param status 완료(COMPLETED) 또는 취소(CANCELED)
 * @param amount 결제 총액 (데브머니 사용분 + 캐시백 사용분)
 * @param walletUsedAmount 데브머니 사용분
 * @param cashbackUsedAmount 캐시백 사용분
 * @param completedAt 결제 완료 시각
 * @param canceledAt 취소 시각. 취소되지 않았으면 null
 */
public record MerchantPaymentItem(
    Long paymentId,
    PaymentStatus status,
    long amount,
    long walletUsedAmount,
    long cashbackUsedAmount,
    LocalDateTime completedAt,
    LocalDateTime canceledAt) {

  public static MerchantPaymentItem from(Payment payment) {
    return new MerchantPaymentItem(
        payment.getId(),
        payment.getStatus(),
        payment.getAmount(),
        payment.getWalletUsedAmount(),
        payment.getCashbackUsedAmount(),
        payment.getCompletedAt(),
        payment.getCanceledAt());
  }
}
