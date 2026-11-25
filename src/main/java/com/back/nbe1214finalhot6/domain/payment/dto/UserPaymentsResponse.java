package com.back.nbe1214finalhot6.domain.payment.dto;

import com.back.nbe1214finalhot6.common.response.PageResponse;

/**
 * 이용자 결제 내역 목록. 점주용과 달리 기간 요약(매출·순매출)은 없고 결제 목록만 담는다.
 *
 * @param payments 내 결제 목록. 최신순
 */
public record UserPaymentsResponse(PageResponse<UserPaymentItem> payments) {}
