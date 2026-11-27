package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantClosure;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantClosureDeduction;
import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.ClosedMerchantReader;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.ClosureDeductionReader;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.DailySettlementReader;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.MonthlySettlementReader;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 11번 대사(해지). 해지 완료(CLOSED) 가맹점마다 돈 관계가 빠짐없이 청산됐는지 본다.
 *
 * <ul>
 *   <li>{@code REQUEST}: 해지 신청 기록이 없다
 *   <li>{@code PENDING}: 지급이 끝나지 않은 월 정산 건이 남아 있다
 *   <li>{@code AFTER}: 해지 신청일 뒤에 결제나 취소가 일 정산에 반영됐다. 해지 정산은 신청일까지만 청산하므로 그 뒤의 돈은 청산되지 않았다
 *   <li>{@code DEDUCTION}: 해지 차감액의 합이 신청일 마지막 일 정산의 부족분(이월)과 다르다
 *   <li>{@code PAYOUT}: (일 정산 확정액 합 - 차감액 합)이 월 정산 지급액 합과 다르다
 * </ul>
 *
 * <p>계좌 입금은 월 지급 대사({@code MONTHLY_PAYOUT})와 계좌 잔액 대사({@code ACCOUNT_BALANCE})가 이미 보므로 비교하지 않는다. 해지
 * 시각 칼럼이 없어 기준일과 상관없이 실행 시점의 해지 완료 가맹점을 모두 본다(잔액 대사와 같은 방식).
 */
@Component
@RequiredArgsConstructor
public class MerchantClosureCheck implements ReconciliationCheck {

  private final ClosedMerchantReader closedMerchantReader;
  private final DailySettlementReader dailyReader;
  private final MonthlySettlementReader monthlyReader;
  private final ClosureDeductionReader deductionReader;

  @Override
  public ReconciliationType type() {
    return ReconciliationType.MERCHANT_CLOSURE;
  }

  // 여러 표를 읽으므로 같은 시점의 데이터로 비교한다
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReconciliationOutcome check(LocalDate date) {
    List<Long> merchantIds = closedMerchantReader.findClosedMerchantIds();
    if (merchantIds.isEmpty()) {
      return new ReconciliationOutcome(0, List.of());
    }

    Map<Long, LocalDate> closureDates =
        closedMerchantReader.findClosures(merchantIds).stream()
            .collect(
                Collectors.toMap(
                    MerchantClosure::getMerchantId, c -> c.getRequestedAt().toLocalDate()));
    Map<Long, List<DailySettlement>> dailies =
        dailyReader.findByMerchantIdIn(merchantIds).stream()
            .collect(Collectors.groupingBy(DailySettlement::getMerchantId));
    Map<Long, List<MonthlySettlement>> monthlies =
        monthlyReader.findByMerchantIdIn(merchantIds).stream()
            .collect(Collectors.groupingBy(MonthlySettlement::getMerchantId));
    Map<Long, Long> deductions =
        deductionReader.findByMerchantIdIn(merchantIds).stream()
            .collect(
                Collectors.groupingBy(
                    MerchantClosureDeduction::getMerchantId,
                    Collectors.summingLong(MerchantClosureDeduction::getAmount)));

    List<String> mismatches = new ArrayList<>();
    for (Long merchantId : merchantIds) {
      String reason =
          reasonOf(
              closureDates.get(merchantId),
              dailies.getOrDefault(merchantId, List.of()),
              monthlies.getOrDefault(merchantId, List.of()),
              deductions.getOrDefault(merchantId, 0L));
      if (reason != null) {
        mismatches.add("merchantId=" + merchantId + ":" + reason);
      }
    }
    return new ReconciliationOutcome(merchantIds.size(), mismatches);
  }

  // 가맹점 한 곳의 불일치 원인. 맞으면 null. 앞선 원인이 있으면 뒤의 비교는 하지 않는다
  private String reasonOf(
      LocalDate closureDate,
      List<DailySettlement> dailies,
      List<MonthlySettlement> monthlies,
      long deductionSum) {
    if (closureDate == null) {
      return "REQUEST";
    }
    if (monthlies.stream().anyMatch(monthly -> !monthly.isPaid())) {
      return "PENDING";
    }
    // 신청 뒤에는 결제가 막히고 취소도 막혀야 한다. 이월만 넘긴 행은 결제·취소가 0원이라 걸리지 않는다
    boolean activityAfterClosure =
        dailies.stream()
            .anyMatch(
                daily ->
                    daily.getSettlementDate().isAfter(closureDate)
                        && (daily.getPaymentTotal() > 0 || daily.getCancelTotal() > 0));
    if (activityAfterClosure) {
      return "AFTER";
    }

    Map<LocalDate, DailySettlement> byDate =
        dailies.stream()
            .collect(Collectors.toMap(DailySettlement::getSettlementDate, Function.identity()));
    long debt =
        byDate.containsKey(closureDate) ? Math.max(0, -byDate.get(closureDate).getCarryOut()) : 0L;
    if (deductionSum != debt) {
      return "DEDUCTION";
    }

    long confirmedSum = dailies.stream().mapToLong(DailySettlement::getConfirmedAmount).sum();
    long payoutSum = monthlies.stream().mapToLong(MonthlySettlement::getPayoutAmount).sum();
    if (confirmedSum - deductionSum != payoutSum) {
      return "PAYOUT";
    }
    return null;
  }
}
