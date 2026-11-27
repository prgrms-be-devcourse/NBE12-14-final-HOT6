package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantClosureDeduction;
import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.AccountLedgerReader;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.ClosureDeductionReader;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.DailySettlementReader;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.MonthlySettlementReader;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
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
 * 10번 대사(월 지급). 기준일까지 끝난 가장 최근 달을 대상으로 월 지급액, 일 정산 확정액의 합, 점주 모의계좌 원장의 정산 입금이 같은지 본다. 대기 건은 아직 지급
 * 전이므로 원장과 비교하지 않는다.
 *
 * <p>해지 정산이 부족분을 갚으려고 지급액에서 차감한 달은 지급액이 확정액의 합보다 작다. 그 차감액을 확정액의 합에서 뺀 값을 기대 지급액으로 본다. 전액
 * 차감되어 월 정산 건이 없는 달은 기대 지급액이 0이라 누락으로 보지 않는다.
 */
@Component
@RequiredArgsConstructor
public class MonthlyPayoutCheck implements ReconciliationCheck {

  private final DailySettlementReader dailyReader;
  private final MonthlySettlementReader monthlyReader;
  private final ClosureDeductionReader deductionReader;
  private final AccountLedgerReader ledgerReader;

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
    Map<Long, Long> deductions =
        deductionReader.findBySettlementMonth(monthStart).stream()
            .collect(
                Collectors.toMap(
                    MerchantClosureDeduction::getMerchantId, MerchantClosureDeduction::getAmount));
    Map<Long, MonthlySettlement> settlements =
        monthlyReader.findBySettlementMonth(monthStart).stream()
            .collect(Collectors.toMap(MonthlySettlement::getMerchantId, Function.identity()));

    List<Long> paidIds =
        settlements.values().stream()
            .filter(MonthlySettlement::isPaid)
            .map(MonthlySettlement::getId)
            .toList();
    Map<Long, Long> payouts = payoutAmounts(paidIds);

    Set<Long> merchantIds = new TreeSet<>(settlements.keySet());
    confirmedSums.forEach(
        (merchantId, sum) -> {
          if (sum - deductions.getOrDefault(merchantId, 0L) > 0) {
            merchantIds.add(merchantId);
          }
        });

    List<String> mismatches = new ArrayList<>();
    for (Long merchantId : merchantIds) {
      long expectedPayout =
          confirmedSums.getOrDefault(merchantId, 0L) - deductions.getOrDefault(merchantId, 0L);
      MonthlySettlement settlement = settlements.get(merchantId);
      if (settlement == null) {
        mismatches.add("merchantId=" + merchantId + ":MISSING");
      } else if (settlement.getPayoutAmount() != expectedPayout) {
        mismatches.add("merchantId=" + merchantId + ":AMOUNT");
      } else if (settlement.isPaid() && !ledgerMatches(settlement, payouts)) {
        mismatches.add("merchantId=" + merchantId + ":LEDGER");
      }
    }
    return new ReconciliationOutcome(merchantIds.size(), mismatches);
  }

  // 지급 완료 건은 점주 계좌 원장에 같은 금액의 정산 입금이 한 줄 있어야 한다
  private boolean ledgerMatches(MonthlySettlement settlement, Map<Long, Long> payouts) {
    Long recorded = payouts.get(settlement.getId());
    return recorded != null && recorded == settlement.getPayoutAmount();
  }

  // 월 정산 건 ID 별 정산 입금액. 원장의 ref_key 가 월 정산 건 ID 다
  private Map<Long, Long> payoutAmounts(List<Long> settlementIds) {
    if (settlementIds.isEmpty()) {
      return Map.of();
    }
    return ledgerReader
        .findByTypeAndRefKeyIn(AccountTransactionType.SETTLEMENT_PAYOUT, settlementIds)
        .stream()
        .collect(Collectors.toMap(AccountTransaction::getRefKey, AccountTransaction::getAmount));
  }
}
