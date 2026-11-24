package com.back.nbe1214finalhot6.domain.wallet.dto;

/** 가맹점 정산 계좌의 잔액. 지금까지 지급받은 월 정산금이 쌓인 금액이다. */
public record MerchantAccountResponse(long balance) {}
