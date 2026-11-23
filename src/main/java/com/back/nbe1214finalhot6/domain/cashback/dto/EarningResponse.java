package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 이용자의 적립 내역 한 건. 제외된 결제도 적립액 0과 제외 사유를 담아 보여 준다.
 *
 * @param earningId 적립 건 ID
 * @param paymentId 결제 ID
 * @param status 적립 상태(예정 / 지급 완료 / 취소 / 회수 / 제외)
 * @param amount 적립액(원). 제외면 0
 * @param appliedRate 접수 시각에 적용한 요율. 제외면 0
 * @param requestedAt 결제 접수 시각
 * @param payableAt 지급 예정 시각. 제외면 null
 * @param paidAt 지급 시각. 지급 전이면 null
 * @param canceledAt 취소 시각. 취소 전이면 null
 * @param exclusionReason 제외 사유. 제외 건에만 있다
 */
public record EarningResponse(
    Long earningId,
    Long paymentId,
    EarningStatus status,
    long amount,
    BigDecimal appliedRate,
    LocalDateTime requestedAt,
    LocalDateTime payableAt,
    LocalDateTime paidAt,
    LocalDateTime canceledAt,
    CashbackExclusionReason exclusionReason) {

  public static EarningResponse from(CashbackEarning earning) {
    return new EarningResponse(
        earning.getId(),
        earning.getPaymentId(),
        earning.getStatus(),
        earning.getAmount(),
        earning.getAppliedRate(),
        earning.getRequestedAt(),
        earning.getPayableAt(),
        earning.getPaidAt(),
        earning.getCanceledAt(),
        earning.getExclusionReason());
  }
}
