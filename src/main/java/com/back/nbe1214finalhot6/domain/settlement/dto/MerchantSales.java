package com.back.nbe1214finalhot6.domain.settlement.dto;

/** 가맹점별 금액 합계. 정산 집계 쿼리의 결과다. */
public record MerchantSales(Long merchantId, Long total) {}
