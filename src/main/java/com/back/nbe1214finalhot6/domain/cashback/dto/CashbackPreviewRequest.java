package com.back.nbe1214finalhot6.domain.cashback.dto;

/**
 * 결제 전 캐시백 미리보기 요청. 결제 도메인이 {@code CashbackReserveRequest} 와 같은 정의로 값을 채운다.
 *
 * @param walletId 지갑 ID (이용자 식별에 쓴다)
 * @param cashbackEligible 적립 대상 가맹점인지
 * @param walletUsedAmount 적립 기준 금액. 데브머니로 결제할 금액(원)
 * @param selfPayment 이용자가 이 가맹점의 점주 본인인지. 본인이면 적립하지 않는다(#301)
 */
public record CashbackPreviewRequest(
    Long walletId, boolean cashbackEligible, long walletUsedAmount, boolean selfPayment) {

  /** 본인 가맹점 결제가 아닌 요청. */
  public CashbackPreviewRequest(Long walletId, boolean cashbackEligible, long walletUsedAmount) {
    this(walletId, cashbackEligible, walletUsedAmount, false);
  }
}
