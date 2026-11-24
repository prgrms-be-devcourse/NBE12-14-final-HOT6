package com.back.nbe1214finalhot6.domain.settlement.dto;

import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlementStatus;
import java.time.LocalDateTime;
import java.time.YearMonth;

public record MonthlySettlementResponse(
    YearMonth settlementMonth,
    long payoutAmount,
    MonthlySettlementStatus status,
    LocalDateTime paidAt) {

  public static MonthlySettlementResponse from(MonthlySettlement settlement) {
    return new MonthlySettlementResponse(
        YearMonth.from(settlement.getSettlementMonth()),
        settlement.getPayoutAmount(),
        settlement.getStatus(),
        settlement.getPaidAt());
  }
}
