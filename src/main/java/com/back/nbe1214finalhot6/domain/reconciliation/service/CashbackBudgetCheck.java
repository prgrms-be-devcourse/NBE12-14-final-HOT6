package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.CashbackBudgetReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 3번 대사(예산). 모든 월·연 예산의 확보 금액이 지급 대기 적립 건의 합과, 지급 금액이 지급 완료와 회수된 적립 건의 합과 같은지 본다. 지급 후 취소는
 * 예산을 되돌리지 않으므로 회수된 건은 지급 금액에 포함하고, 지급 전에 취소된 건과 제외된 건은 어느 쪽에도 넣지 않는다. 이월과 예산 이동은 확보·지급
 * 금액을 바꾸지 않아서 비교에 영향이 없다. 기준일과 상관없이 실행 시점의 전체를 본다.
 */
@Component
@RequiredArgsConstructor
public class CashbackBudgetCheck implements ReconciliationCheck {

  private static final List<EarningStatus> PAID_STATUSES =
      List.of(EarningStatus.PAID, EarningStatus.CLAWED_BACK);

  private final CashbackBudgetReader budgetReader;

  @Override
  public ReconciliationType type() {
    return ReconciliationType.CASHBACK_BUDGET;
  }

  // 예산과 적립 건을 여러 쿼리로 읽으므로 같은 시점의 데이터로 읽는다
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReconciliationOutcome check(LocalDate date) {
    List<String> mismatches = new ArrayList<>();
    budgetReader
        .findMonthlyReservedMismatches(EarningStatus.PENDING)
        .forEach(ym -> mismatches.add(monthRef(ym, "RESERVED")));
    budgetReader
        .findMonthlyPaidMismatches(PAID_STATUSES)
        .forEach(ym -> mismatches.add(monthRef(ym, "PAID")));
    budgetReader
        .findAnnualReservedMismatches(EarningStatus.PENDING)
        .forEach(year -> mismatches.add("year=" + year + ":RESERVED"));
    budgetReader
        .findAnnualPaidMismatches(PAID_STATUSES)
        .forEach(year -> mismatches.add("year=" + year + ":PAID"));

    // 예산 행마다 확보 금액과 지급 금액 두 항목을 비교한다
    long checked = (budgetReader.count() + budgetReader.countAnnual()) * 2;
    return new ReconciliationOutcome(checked, mismatches);
  }

  private String monthRef(int yearMonth, String reason) {
    return String.format("month=%d-%02d:%s", yearMonth / 100, yearMonth % 100, reason);
  }
}
