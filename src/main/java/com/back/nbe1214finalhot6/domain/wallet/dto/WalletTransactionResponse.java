package com.back.nbe1214finalhot6.domain.wallet.dto;

import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import java.time.LocalDateTime;

/**
 * 지갑 거래 내역 한 줄.
 *
 * @param transactionId 거래 ID
 * @param type 거래 종류(충전, 환불, 결제, 결제 취소, 캐시백 적립·회수)
 * @param amount 부호 있는 금액. 입금은 +, 차감은 -
 * @param balanceAfter 이 거래 직후의 잔액. {@code cashback} 이 true 면 캐시백 잔액, false 면 데브머니 잔액
 * @param cashback 캐시백 잔액을 바꾼 거래인지. false 면 데브머니 잔액을 바꿈
 * @param refKey 거래의 원인이 된 건의 ID. 결제·결제 취소는 결제 ID, 캐시백 적립·회수는 적립 건 ID 이다
 * @param paymentId 결제 ID. 결제·결제 취소 거래에만 있고 나머지는 null. 캐시백 적립 내역 API 의 paymentId 와 맞춰 볼 때 쓴다
 * @param createdAt 거래 시각
 */
public record WalletTransactionResponse(
    Long transactionId,
    WalletTransactionType type,
    long amount,
    long balanceAfter,
    boolean cashback,
    Long refKey,
    Long paymentId,
    LocalDateTime createdAt) {

  public static WalletTransactionResponse from(WalletTransaction transaction) {
    return new WalletTransactionResponse(
        transaction.getId(),
        transaction.getType(),
        transaction.getAmount(),
        transaction.getBalanceAfter(),
        transaction.getType().affectsCashback(),
        transaction.getRefKey(),
        paymentIdOf(transaction),
        transaction.getCreatedAt());
  }

  // 지갑 서비스는 결제 ID 를 원장의 refKey 에만 남기고 payment_id 컬럼은 채우지 않는다. 그래서 컬럼 값이 있으면 그것을,
  // 없으면 결제 관련 거래에 한해 refKey 를 결제 ID 로 본다. 캐시백 적립·회수의 refKey 는 적립 건 ID 라서 결제 ID 가 아니다
  private static Long paymentIdOf(WalletTransaction transaction) {
    if (transaction.getPaymentId() != null) {
      return transaction.getPaymentId();
    }
    return switch (transaction.getType()) {
      case PAYMENT_DEVMONEY,
          PAYMENT_CASHBACK,
          PAYMENT_CANCEL_DEVMONEY,
          PAYMENT_CANCEL_CASHBACK -> transaction.getRefKey();
      default -> null;
    };
  }
}
