package com.back.nbe1214finalhot6.domain.payment.dto;

import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;

/**
 * 이용자 결제 내역의 결제 한 건. 이용자 본인의 지갑 기준이라 지갑 번호는 담지 않고, 어디서 결제했는지 알 수 있게 가맹점명을 담는다.
 *
 * @param paymentId 결제 번호
 * @param merchantName 결제한 가맹점 이름. 가맹점을 찾지 못하면 null
 * @param status 완료(COMPLETED) 또는 취소(CANCELED)
 * @param amount 결제 총액 (데브머니 사용분 + 캐시백 사용분)
 * @param walletUsedAmount 데브머니 사용분
 * @param cashbackUsedAmount 캐시백 사용분
 * @param cashbackEarnedAmount 이 결제로 적립(예정)된 캐시백. 적립이 없으면 null 또는 0
 * @param completedAt 결제 완료 시각
 * @param canceledAt 취소 시각. 취소되지 않았으면 null
 */
public record UserPaymentItem(
    Long paymentId,
    String merchantName,
    PaymentStatus status,
    long amount,
    long walletUsedAmount,
    long cashbackUsedAmount,
    Long cashbackEarnedAmount,
    LocalDateTime completedAt,
    LocalDateTime canceledAt) {

  public static UserPaymentItem of(Payment payment, String merchantName) {
    return new UserPaymentItem(
        payment.getId(),
        merchantName,
        payment.getStatus(),
        payment.getAmount(),
        payment.getWalletUsedAmount(),
        payment.getCashbackUsedAmount(),
        payment.getCashbackEarnedAmount(),
        payment.getCompletedAt(),
        payment.getCanceledAt());
  }
}
