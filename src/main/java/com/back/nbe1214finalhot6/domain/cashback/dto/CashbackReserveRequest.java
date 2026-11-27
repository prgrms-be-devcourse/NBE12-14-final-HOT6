package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.time.LocalDateTime;

/**
 * 결제 한 건에 대한 캐시백 적립 요청. 결제 도메인이 결제 트랜잭션 안에서 보낸다.
 *
 * @param paymentId 결제 ID
 * @param walletId 지갑 ID (이용자 식별에 쓴다)
 * @param cashbackEligible 적립 대상 가맹점인지
 * @param walletUsedAmount 적립 기준 금액. 데브머니로 결제한 금액(원)
 * @param requestedAt 최초 서버 접수 시각(KST). 요율과 월 기준이 된다
 * @param selfPayment 이용자가 이 가맹점의 점주 본인인지. 본인이면 적립하지 않는다(#301)
 */
public record CashbackReserveRequest(
    Long paymentId,
    Long walletId,
    boolean cashbackEligible,
    long walletUsedAmount,
    LocalDateTime requestedAt,
    boolean selfPayment) {

  /** 본인 가맹점 결제가 아닌 요청. */
  public CashbackReserveRequest(
      Long paymentId,
      Long walletId,
      boolean cashbackEligible,
      long walletUsedAmount,
      LocalDateTime requestedAt) {
    this(paymentId, walletId, cashbackEligible, walletUsedAmount, requestedAt, false);
  }
}
