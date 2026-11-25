package com.back.nbe1214finalhot6.domain.merchant.dto;

import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;

/**
 * 해지 신청 전에 보여 주는 정산 현황.
 *
 * @param unpaidAmount 아직 계좌로 나가지 않은 정산금(지급 대기 월 정산 + 월 정산에 묶이지 않은 일 정산 확정액)
 * @param carryDebt 취소·환수로 쌓여 앞으로 정산금에서 갚아야 하는 금액(0 이상)
 * @param accountBalance 정산 계좌의 현재 잔액
 * @param closable 갚을 금액을 정산금과 계좌 잔액으로 모두 감당할 수 있어 신청이 가능한지
 * @param shortfall 감당하지 못하는 부족 금액. 신청이 가능하면 0
 */
public record ClosurePreviewResponse(
    MerchantStatus status,
    long unpaidAmount,
    long carryDebt,
    long accountBalance,
    boolean closable,
    long shortfall) {}
