package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.DailySettlementReader;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.MonthlySettlementReader;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.WalletLedgerReader;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransactionType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 월 지급 대사. 기준일까지 끝난 가장 최근 달을 대상으로 월 지급액, 일 정산 확정액의 합, 지갑 원장의 입금과 출금이 같은지 본다. 대기 건은 아직 지급
 * 전이므로 원장과 비교하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class MonthlyPayoutCheck implements ReconciliationCheck {

  private final DailySettlementReader dailyReader;
  private final MonthlySettlementReader monthlyReader;
  private final WalletLedgerReader ledgerReader;

  @Override
  public ReconciliationType type() {
    return ReconciliationType.MONTHLY_PAYOUT;
  }

  @Override
  public ReconciliationOutcome check(LocalDate date) {
    // 기준일까지 끝난 가장 최근 달. 말일이 기준일이면 그 달을 본다.
    YearMonth month = YearMonth.from(date.plusDays(1)).minusMonths(1);
    LocalDate monthStart = month.atDay(1);

    Map<Long, Long> confirmedSums =
        dailyReader.findBySettlementDateBetween(monthStart, month.atEndOfMonth()).stream()
            .collect(
                Collectors.groupingBy(
                    DailySettlement::getMerchantId,
                    Collectors.summingLong(DailySettlement::getConfirmedAmount)));
    Map<Long, MonthlySettlement> settlements =
        monthlyReader.findBySettlementMonth(monthStart).stream()
            .collect(Collectors.toMap(MonthlySettlement::getMerchantId, Function.identity()));

    List<Long> paidIds =
        settlements.values().stream()
            .filter(MonthlySettlement::isPaid)
            .map(MonthlySettlement::getId)
            .toList();
    Map<Long, Long> deposits =
        ledgerAmounts(MerchantWalletTransactionType.SETTLEMENT_PAYOUT, paidIds);
    Map<Long, Long> withdrawals =
        ledgerAmounts(MerchantWalletTransactionType.PAYOUT_TO_ACCOUNT, paidIds);

    Set<Long> merchantIds = new TreeSet<>(settlements.keySet());
    confirmedSums.forEach(
        (merchantId, sum) -> {
          if (sum > 0) {
            merchantIds.add(merchantId);
          }
        });

    List<String> mismatches = new ArrayList<>();
    for (Long merchantId : merchantIds) {
      long confirmedSum = confirmedSums.getOrDefault(merchantId, 0L);
      MonthlySettlement settlement = settlements.get(merchantId);
      if (settlement == null) {
        mismatches.add("merchantId=" + merchantId + ":MISSING");
      } else if (settlement.getPayoutAmount() != confirmedSum) {
        mismatches.add("merchantId=" + merchantId + ":AMOUNT");
      } else if (settlement.isPaid() && !ledgerMatches(settlement, deposits, withdrawals)) {
        mismatches.add("merchantId=" + merchantId + ":LEDGER");
      }
    }
    return new ReconciliationOutcome(merchantIds.size(), mismatches);
  }

  private boolean ledgerMatches(
      MonthlySettlement settlement, Map<Long, Long> deposits, Map<Long, Long> withdrawals) {
    long payout = settlement.getPayoutAmount();
    return deposits.get(settlement.getId()) != null
        && deposits.get(settlement.getId()) == payout
        && withdrawals.get(settlement.getId()) != null
        && withdrawals.get(settlement.getId()) == -payout;
  }

  private Map<Long, Long> ledgerAmounts(MerchantWalletTransactionType type, List<Long> refKeys) {
    if (refKeys.isEmpty()) {
      return Map.of();
    }
    return ledgerReader.findByTypeAndRefKeyIn(type, refKeys).stream()
        .collect(
            Collectors.toMap(
                MerchantWalletTransaction::getRefKey, MerchantWalletTransaction::getAmount));
  }
}
