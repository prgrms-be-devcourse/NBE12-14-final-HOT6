package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.time.LocalDateTime;

/**
 * 운영자의 지급 대기 현황.
 *
 * @param pendingCount 지급 대기(적립 예정) 건수
 * @param pendingAmount 지급 대기 금액 합계(원)
 * @param dueCount 대기 중 지급 예정 시각이 지나 지금 지급할 수 있는 건수. 실행한 뒤에도 남아 있으면 지급에 실패한 건이다
 * @param dueAmount 지금 지급할 수 있는 금액 합계(원)
 * @param nextPayableAt 아직 시각이 되지 않은 건 중 가장 이른 지급 예정 시각. 없으면 null
 */
public record PayoutStatusResponse(
    long pendingCount,
    long pendingAmount,
    long dueCount,
    long dueAmount,
    LocalDateTime nextPayableAt) {}
