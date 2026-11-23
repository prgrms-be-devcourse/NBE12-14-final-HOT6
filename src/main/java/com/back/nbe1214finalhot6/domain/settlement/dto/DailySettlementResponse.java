package com.back.nbe1214finalhot6.domain.settlement.dto;

import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import java.time.LocalDate;

public record DailySettlementResponse(
    LocalDate settlementDate,
    long paymentTotal,
    long cancelTotal,
    long netSales,
    long carryIn,
    long confirmedAmount,
    long carryOut) {

  public static DailySettlementResponse from(DailySettlement settlement) {
    return new DailySettlementResponse(
        settlement.getSettlementDate(),
        settlement.getPaymentTotal(),
        settlement.getCancelTotal(),
        settlement.getNetSales(),
        settlement.getCarryIn(),
        settlement.getConfirmedAmount(),
        settlement.getCarryOut());
  }
}
