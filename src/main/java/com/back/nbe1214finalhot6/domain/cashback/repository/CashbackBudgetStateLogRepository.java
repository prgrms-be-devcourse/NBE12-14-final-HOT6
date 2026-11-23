package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackBudgetStateLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashbackBudgetStateLogRepository
    extends JpaRepository<CashbackBudgetStateLog, Long> {

  /** 한 월 예산의 상태 전이 이력을 오래된 순으로 돌려준다. */
  List<CashbackBudgetStateLog> findAllByMonthlyBudgetIdOrderByIdAsc(Long monthlyBudgetId);
}
