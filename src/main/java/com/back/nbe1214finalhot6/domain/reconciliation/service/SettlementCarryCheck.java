package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.DailySettlementReader;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 9번 대사. 확정액과 이월이 순매출과 전일 이월로 설명되는지, 전일 이월이 어제 장부와 이어지는지 본다. */
@Component
@RequiredArgsConstructor
public class SettlementCarryCheck implements ReconciliationCheck {

  private final DailySettlementReader settlementReader;

  @Override
  public ReconciliationType type() {
    return ReconciliationType.SETTLEMENT_CARRY;
  }

  @Override
  public ReconciliationOutcome check(LocalDate date) {
    List<DailySettlement> today = settlementReader.findBySettlementDate(date);
    List<DailySettlement> yesterday = settlementReader.findBySettlementDate(date.minusDays(1));
    Map<Long, DailySettlement> previous =
        yesterday.stream()
            .collect(Collectors.toMap(DailySettlement::getMerchantId, Function.identity()));
    Map<Long, DailySettlement> current =
        today.stream()
            .collect(Collectors.toMap(DailySettlement::getMerchantId, Function.identity()));

    List<String> mismatches = new ArrayList<>();
    for (DailySettlement row : today) {
      long expectedCarryIn =
          previous.containsKey(row.getMerchantId())
              ? previous.get(row.getMerchantId()).getCarryOut()
              : 0L;
      boolean balanced =
          row.getConfirmedAmount() + row.getCarryOut() == row.getNetSales() + row.getCarryIn();
      if (!balanced || row.getCarryIn() != expectedCarryIn) {
        mismatches.add("merchantId=" + row.getMerchantId());
      }
    }
    // 어제 부족분을 넘긴 가맹점은 매출이 없어도 오늘 장부가 있어야 한다
    int missingCount = 0;
    for (DailySettlement row : yesterday) {
      if (row.getCarryOut() < 0 && !current.containsKey(row.getMerchantId())) {
        missingCount++;
        mismatches.add("merchantId=" + row.getMerchantId());
      }
    }
    return new ReconciliationOutcome(today.size() + missingCount, mismatches);
  }
}
