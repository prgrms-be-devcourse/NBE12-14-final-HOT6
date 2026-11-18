package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackAnnualBudget;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashbackAnnualBudgetRepository extends JpaRepository<CashbackAnnualBudget, Long> {

  Optional<CashbackAnnualBudget> findByBudgetYear(int budgetYear);
}
