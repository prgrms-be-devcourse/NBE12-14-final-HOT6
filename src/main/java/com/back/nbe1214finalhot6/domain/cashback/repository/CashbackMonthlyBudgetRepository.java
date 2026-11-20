package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashbackMonthlyBudgetRepository
    extends JpaRepository<CashbackMonthlyBudget, Long> {

  Optional<CashbackMonthlyBudget> findByBudgetYearAndBudgetMonth(int budgetYear, int budgetMonth);

  /**
   * 행을 잠그고 읽는다. 배정을 바꾸는 동안 결제의 예약 금액 변경이 끼어들어 덮어써지지 않게 한다.
   * 연 예산 행을 먼저 잠근 뒤에 호출한다(잠금 순서: 연 -> 월).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<CashbackMonthlyBudget> findWithLockByBudgetYearAndBudgetMonth(
      int budgetYear, int budgetMonth);

  List<CashbackMonthlyBudget> findAllByBudgetYearOrderByBudgetMonth(int budgetYear);

  /** 해당 연도에서 지정한 달을 뺀 월 배정 합계. 배정이 없으면 0 이다. */
  @Query(
      "select coalesce(sum(m.allocatedAmount), 0) from CashbackMonthlyBudget m"
          + " where m.budgetYear = :year and m.budgetMonth <> :month")
  long sumAllocatedExcluding(@Param("year") int year, @Param("month") int month);

  /** 해당 연·월보다 앞선 달 중 기본 요율이 있는 가장 가까운 달. 기본 요율을 이전 달에서 이어받을 때 쓴다. */
  @Query(
      """
      select m from CashbackMonthlyBudget m
      where m.baseRate is not null
        and (m.budgetYear < :year or (m.budgetYear = :year and m.budgetMonth < :month))
      order by m.budgetYear desc, m.budgetMonth desc
      limit 1
      """)
  Optional<CashbackMonthlyBudget> findLatestBaseRateBefore(
      @Param("year") int year, @Param("month") int month);

  /** 해당 연도의 월 배정 합계. */
  @Query(
      "select coalesce(sum(m.allocatedAmount), 0) from CashbackMonthlyBudget m"
          + " where m.budgetYear = :year")
  long sumAllocated(@Param("year") int year);
}
