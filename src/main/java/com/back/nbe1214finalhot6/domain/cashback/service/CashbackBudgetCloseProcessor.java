package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * 월 예산 한 건의 마감 처리. 건별 트랜잭션의 경계이며, 월 마감 배치({@link CashbackBudgetCloseService})가 건마다 호출한다. 트랜잭션이 걸리도록 배치와
 * 다른 빈에 둔다.
 */
@Service
@RequiredArgsConstructor
public class CashbackBudgetCloseProcessor {

  private final CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  private final CashbackAnnualBudgetRepository annualBudgetRepository;
  private final CashbackBudgetTransferService transferService;

  /**
   * 지난 달의 월 예산을 마감하고 남은 예산 전액을 다음 달로 이월한다. 새 트랜잭션에서 실행하며 마감과 이월이 한 번에 커밋되거나 한 번에 롤백된다.
   *
   * <p>다음 달 월 예산이 없으면(12월이면 다음 해 연 예산도) 마감하지 않고 보류한다. 마감은 {@code where status <> CLOSED} 조건부 UPDATE 라서 같은
   * 달을 두 곳에서 처리해도 한쪽만 이월한다. 마감한 뒤에는 그 달의 예산 확보가 거절되므로(마감 달은 적립을 받지 않는다) 남은 예산이 더 줄지 않는다. 이월한 뒤 그 달의
   * 적립 건이 취소돼 확보가 반환되면 그 금액은 마감된 달에 남고 다시 이월하지 않는다. 남은 예산이 0 이면 이월 없이 마감만 한다.
   *
   * <p>12월에서 넘기면 연 예산도 맞추므로 연 예산 행을 먼저 잠근다(잠금 순서: 연 -> 월). 적립 중단·공지 상태의 달도 마감하고 남은 예산을 넘긴다.
   */
  @Transactional
  public BudgetCloseOutcome close(Long budgetId) {
    CashbackMonthlyBudget budget = monthlyBudgetRepository.findById(budgetId).orElse(null);
    if (budget == null || budget.getStatus() == BudgetStatus.CLOSED) {
      return BudgetCloseOutcome.SKIPPED;
    }
    YearMonth next = YearMonth.of(budget.getBudgetYear(), budget.getBudgetMonth()).plusMonths(1);
    CashbackMonthlyBudget target =
        monthlyBudgetRepository
            .findByBudgetYearAndBudgetMonth(next.getYear(), next.getMonthValue())
            .orElse(null);
    boolean crossYear = next.getYear() != budget.getBudgetYear();
    if (target == null || (crossYear && !annualBudgetRepository.existsByBudgetYear(next.getYear()))) {
      return BudgetCloseOutcome.SKIPPED;
    }
    if (crossYear) {
      annualBudgetRepository.findWithLockByBudgetYear(budget.getBudgetYear());
    }

    if (monthlyBudgetRepository.closeIfOpen(budgetId, BudgetStatus.CLOSED) == 0) {
      return BudgetCloseOutcome.SKIPPED;
    }
    long remaining = monthlyBudgetRepository.findRemainingAmount(budgetId).orElse(0L);
    if (remaining > 0) {
      transferService.transferAutomatically(budget, target, remaining);
    }
    return BudgetCloseOutcome.CLOSED;
  }
}
