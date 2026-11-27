package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 운영자가 보는 적립 한 건. 이용자는 이름 없이 지갑 번호로만 구분한다.
 *
 * @param walletUsedAmount 적립 기준 금액(데브머니로 결제한 금액)
 * @param amount 적립액(원). 제외면 0
 * @param appliedRate 접수 시각에 적용한 요율. 제외면 0
 * @param payableAt 지급 예정 시각. 제외면 null
 * @param exclusionReason 제외 사유. 제외 건에만 있다
 */
public record AdminEarningResponse(
    Long earningId,
    Long paymentId,
    Long walletId,
    EarningStatus status,
    long walletUsedAmount,
    long amount,
    BigDecimal appliedRate,
    LocalDateTime requestedAt,
    LocalDateTime payableAt,
    LocalDateTime paidAt,
    LocalDateTime canceledAt,
    CashbackExclusionReason exclusionReason) {

  public static AdminEarningResponse from(CashbackEarning earning) {
    return new AdminEarningResponse(
        earning.getId(),
        earning.getPaymentId(),
        earning.getWalletId(),
        earning.getStatus(),
        earning.getWalletUsedAmount(),
        earning.getAmount(),
        earning.getAppliedRate(),
        earning.getRequestedAt(),
        earning.getPayableAt(),
        earning.getPaidAt(),
        earning.getCanceledAt(),
        earning.getExclusionReason());
  }
}
