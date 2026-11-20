package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackAnnualBudget;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CashbackAnnualBudgetRepository extends JpaRepository<CashbackAnnualBudget, Long> {

  Optional<CashbackAnnualBudget> findByBudgetYear(int budgetYear);

  /**
   * 행을 잠그고 읽는다. 총액 변경이나 월 배정 합계 검증 중에 결제의 예약 금액 변경이 끼어들어 덮어써지지 않게 한다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<CashbackAnnualBudget> findWithLockByBudgetYear(int budgetYear);
}
