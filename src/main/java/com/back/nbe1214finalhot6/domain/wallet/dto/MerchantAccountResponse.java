package com.back.nbe1214finalhot6.domain.wallet.dto;

/** 점주 정산 계좌(점주의 모의계좌)의 잔액. 지금까지 지급받은 월 정산금이 쌓인 금액이다. */
public record MerchantAccountResponse(long balance) {}
