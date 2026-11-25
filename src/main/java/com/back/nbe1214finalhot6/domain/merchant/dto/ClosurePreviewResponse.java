package com.back.nbe1214finalhot6.domain.merchant.dto;

import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;

/**
 * 해지 신청 전에 보여 주는 정산 현황.
 *
 * @param unpaidAmount 아직 계좌로 나가지 않은 정산금(지급 대기 월 정산 + 월 정산에 묶이지 않은 일 정산 확정액)
 * @param offsettableAmount 그중 갚을 금액에서 차감할 수 있는 정산금. 월 정산 건이 아직 없는 일 정산 확정액이며, 이미 만들어진 지급 대기 건은 금액을 고치지
 *     않으므로 빠진다
 * @param carryDebt 취소·환수로 쌓여 앞으로 정산금에서 갚아야 하는 금액(0 이상)
 * @param accountBalance 정산 계좌의 현재 잔액(참고용이며 갚을 금액에 쓰지 않는다)
 * @param closable 갚을 금액을 차감할 수 있는 정산금({@code offsettableAmount})으로 모두 감당할 수 있어 신청이 가능한지
 * @param shortfall 차감할 수 있는 정산금으로 감당하지 못하는 부족 금액. 신청이 가능하면 0
 */
public record ClosurePreviewResponse(
    MerchantStatus status,
    long unpaidAmount,
    long offsettableAmount,
    long carryDebt,
    long accountBalance,
    boolean closable,
    long shortfall) {}
