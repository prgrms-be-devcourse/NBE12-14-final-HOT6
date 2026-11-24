package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 대사가 캐시백 월·연 예산을 읽기만 하는 저장소. 예산의 확보·지급 금액과 적립 건의 합계는 한 쿼리에서 비교한다. 월은 연도와 월을 합친 정수(예: 2026년
 * 11월은 202611)로 돌려준다.
 */
public interface CashbackBudgetReader extends Repository<CashbackMonthlyBudget, Long> {

  long count();

  @Query("select count(a) from CashbackAnnualBudget a")
  long countAnnual();

  @Query(
      """
      select m.budgetYear * 100 + m.budgetMonth from CashbackMonthlyBudget m
      where m.reservedAmount <> (
        select coalesce(sum(e.amount), 0L) from CashbackEarning e
        where e.status = :status
          and year(e.limitMonth) = m.budgetYear and month(e.limitMonth) = m.budgetMonth)
      order by m.budgetYear, m.budgetMonth
      """)
  List<Integer> findMonthlyReservedMismatches(@Param("status") EarningStatus status);

  @Query(
      """
      select m.budgetYear * 100 + m.budgetMonth from CashbackMonthlyBudget m
      where m.paidAmount <> (
        select coalesce(sum(e.amount), 0L) from CashbackEarning e
        where e.status in :statuses
          and year(e.limitMonth) = m.budgetYear and month(e.limitMonth) = m.budgetMonth)
      order by m.budgetYear, m.budgetMonth
      """)
  List<Integer> findMonthlyPaidMismatches(@Param("statuses") Collection<EarningStatus> statuses);

  @Query(
      """
      select a.budgetYear from CashbackAnnualBudget a
      where a.reservedAmount <> (
        select coalesce(sum(e.amount), 0L) from CashbackEarning e
        where e.status = :status and year(e.limitMonth) = a.budgetYear)
      order by a.budgetYear
      """)
  List<Integer> findAnnualReservedMismatches(@Param("status") EarningStatus status);

  @Query(
      """
      select a.budgetYear from CashbackAnnualBudget a
      where a.paidAmount <> (
        select coalesce(sum(e.amount), 0L) from CashbackEarning e
        where e.status in :statuses and year(e.limitMonth) = a.budgetYear)
      order by a.budgetYear
      """)
  List<Integer> findAnnualPaidMismatches(@Param("statuses") Collection<EarningStatus> statuses);
}
