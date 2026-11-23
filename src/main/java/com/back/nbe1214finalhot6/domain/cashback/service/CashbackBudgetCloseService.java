package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetCloseRunResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 월 마감 배치. 달이 바뀐 뒤 지난 달의 월 예산을 마감하고 남은 예산을 다음 달로 자동 이월한다({@code AUTO}).
 *
 * <p>이 클래스는 트랜잭션 없이 대상 월 예산 ID 만 고르고, 건마다 {@link CashbackBudgetCloseProcessor} 를 호출한다. 오래된 달부터 처리해서, 여러 달이 밀려
 * 있어도 앞 달에서 넘어온 금액이 다음 달에 포함된 채 이월된다. 한 건이 실패해도 그 건만 롤백되어 다음 배치에서 다시 시도되고 나머지는 계속 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashbackBudgetCloseService {

  private final CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  private final CashbackBudgetCloseProcessor processor;
  private final Clock clock;

  /** 이미 지난 달 중 마감하지 않은 월 예산을 모두 처리한다. */
  public BudgetCloseRunResponse run() {
    YearMonth thisMonth = YearMonth.now(clock);
    List<Long> ids =
        monthlyBudgetRepository.findClosableIds(
            thisMonth.getYear(), thisMonth.getMonthValue(), BudgetStatus.CLOSED);
    int closed = 0;
    int skipped = 0;
    int failed = 0;
    for (Long id : ids) {
      try {
        if (processor.close(id) == BudgetCloseOutcome.CLOSED) {
          closed++;
        } else {
          skipped++;
        }
      } catch (RuntimeException e) {
        failed++;
        log.warn("캐시백 월 마감 실패 budgetId={}: {}", id, e.getMessage());
      }
    }
    return new BudgetCloseRunResponse(closed, skipped, failed);
  }
}
