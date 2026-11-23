package com.back.nbe1214finalhot6.domain.cashback.dto;

/**
 * 이번 달 적립 요약. 월은 결제 접수 시각의 월(월 적립 한도와 같은 기준)이고, 취소·회수된 건은 금액에 넣지 않는다.
 *
 * @param earnedAmount 이번 달 적립액(적립 예정 + 지급 완료)
 * @param pendingAmount 적립 예정 금액
 * @param paidAmount 지급 완료 금액
 * @param excludedCount 적립에서 제외된 결제 건수(제외 건의 적립액은 0)
 */
public record CashbackSummaryResponse(
    int year,
    int month,
    long earnedAmount,
    long pendingAmount,
    long paidAmount,
    long excludedCount) {}
