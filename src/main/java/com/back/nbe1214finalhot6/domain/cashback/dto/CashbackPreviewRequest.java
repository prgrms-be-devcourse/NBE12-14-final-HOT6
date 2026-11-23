package com.back.nbe1214finalhot6.domain.cashback.dto;

/**
 * 결제 전 캐시백 미리보기 요청. 결제 도메인이 {@code CashbackReserveRequest} 와 같은 정의로 값을 채운다.
 *
 * @param walletId 지갑 ID (이용자 식별에 쓴다)
 * @param cashbackEligible 적립 대상 가맹점인지
 * @param walletUsedAmount 적립 기준 금액. 데브머니로 결제할 금액(원)
 */
public record CashbackPreviewRequest(Long walletId, boolean cashbackEligible, long walletUsedAmount) {}
