package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackBudgetTransfer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashbackBudgetTransferRepository
    extends JpaRepository<CashbackBudgetTransfer, Long> {

  /** 해당 연도의 월에서 넘긴 이월 이력을 최근 이월순으로 돌려준다. 같은 시각이면 나중에 남긴 것이 먼저다. */
  @Query(
      """
      select t from CashbackBudgetTransfer t
      where t.fromMonthlyBudgetId in
        (select m.id from CashbackMonthlyBudget m where m.budgetYear = :year)
      order by t.createdAt desc, t.id desc
      """)
  List<CashbackBudgetTransfer> findAllFromYear(@Param("year") int year);
}
