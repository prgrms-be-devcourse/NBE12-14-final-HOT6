package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.DailySettlementReader;
import com.back.nbe1214finalhot6.domain.settlement.dto.MerchantSales;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.MerchantSalesRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 8번 대사. 일 정산 장부의 결제·취소·순매출이 결제 원본을 다시 집계한 값과 같은지 본다. */
@Component
@RequiredArgsConstructor
public class DailyNetSalesCheck implements ReconciliationCheck {

  private static final List<PaymentStatus> COMPLETED_STATUSES =
      List.of(PaymentStatus.COMPLETED, PaymentStatus.CANCELED);

  private final MerchantSalesRepository salesRepository;
  private final DailySettlementReader settlementReader;

  @Override
  public ReconciliationType type() {
    return ReconciliationType.DAILY_NET_SALES;
  }

  @Override
  public ReconciliationOutcome check(LocalDate date) {
    LocalDateTime start = date.atStartOfDay();
    LocalDateTime end = date.plusDays(1).atStartOfDay();
    Map<Long, Long> payments =
        toMap(salesRepository.sumByCompletedAt(start, end, COMPLETED_STATUSES));
    Map<Long, Long> cancels =
        toMap(salesRepository.sumByCanceledAt(start, end, PaymentStatus.CANCELED));
    Map<Long, DailySettlement> ledger =
        settlementReader.findBySettlementDate(date).stream()
            .collect(Collectors.toMap(DailySettlement::getMerchantId, Function.identity()));

    Set<Long> merchantIds = new TreeSet<>();
    merchantIds.addAll(payments.keySet());
    merchantIds.addAll(cancels.keySet());
    merchantIds.addAll(ledger.keySet());

    List<String> mismatches = new ArrayList<>();
    for (Long merchantId : merchantIds) {
      long payment = payments.getOrDefault(merchantId, 0L);
      long cancel = cancels.getOrDefault(merchantId, 0L);
      DailySettlement row = ledger.get(merchantId);
      boolean matched =
          row == null
              ? payment == 0 && cancel == 0
              : row.getPaymentTotal() == payment
                  && row.getCancelTotal() == cancel
                  && row.getNetSales() == payment - cancel;
      if (!matched) {
        mismatches.add("merchantId=" + merchantId);
      }
    }
    return new ReconciliationOutcome(merchantIds.size(), mismatches);
  }

  private Map<Long, Long> toMap(List<MerchantSales> sales) {
    return sales.stream()
        .collect(Collectors.toMap(MerchantSales::merchantId, MerchantSales::total));
  }
}
