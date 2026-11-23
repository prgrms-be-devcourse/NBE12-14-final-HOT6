package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

  /**
   * 적립을 받는 상태이고 남은 예산(배정 + 이월 유입 - 이월 유출 - 예약 - 지급)이 금액 이상일 때만 확보 금액을 늘린다. 확보하면 1, 아니면 0 을 돌려준다.
   * 연 예산 확보를 먼저 한 뒤에 호출한다(잠금 순서: 연 -> 월). 영속성 컨텍스트를 비우지 않는다(연 예산 쪽 설명 참고).
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackMonthlyBudget m set m.reservedAmount = m.reservedAmount + :amount
      where m.budgetYear = :year and m.budgetMonth = :month
        and m.status in :statuses
        and m.allocatedAmount + m.carriedInAmount - m.carriedOutAmount
            - m.reservedAmount - m.paidAmount >= :amount
      """)
  int reserve(
      @Param("year") int year,
      @Param("month") int month,
      @Param("amount") long amount,
      @Param("statuses") Collection<BudgetStatus> statuses);

  /** 확보한 금액을 되돌린다. 확보한 금액보다 많이 빼야 하면 0 을 돌려주고 바꾸지 않는다. */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackMonthlyBudget m set m.reservedAmount = m.reservedAmount - :amount
      where m.budgetYear = :year and m.budgetMonth = :month and m.reservedAmount >= :amount
      """)
  int release(
      @Param("year") int year, @Param("month") int month, @Param("amount") long amount);

  /** 월 예산의 현재 상태. 엔티티가 아니라 값만 읽어서 컨텍스트에 남은 낡은 엔티티의 영향을 받지 않는다. */
  @Query(
      "select m.status from CashbackMonthlyBudget m"
          + " where m.budgetYear = :year and m.budgetMonth = :month")
  Optional<BudgetStatus> findStatus(@Param("year") int year, @Param("month") int month);

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

  /**
   * 남은 예산이 금액 이상일 때만 다음 달로 넘긴 금액을 늘린다. 넘기면 1, 모자라거나 월 예산이 없으면 0 을 돌려준다. 한 문장의 조건부 UPDATE 라서
   * 동시에 들어온 확보·복원과 섞여도 남은 예산이 음수가 되지 않는다. 적립 상태와 상관없이 넘길 수 있다. 영속성 컨텍스트를 비우지 않는다.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackMonthlyBudget m set m.carriedOutAmount = m.carriedOutAmount + :amount
      where m.budgetYear = :year and m.budgetMonth = :month
        and m.allocatedAmount + m.carriedInAmount - m.carriedOutAmount
            - m.reservedAmount - m.paidAmount >= :amount
      """)
  int transferOut(
      @Param("year") int year, @Param("month") int month, @Param("amount") long amount);

  /** 다른 달에서 넘어온 금액을 늘린다. 늘리면 1, 월 예산이 없으면 0 을 돌려준다. */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackMonthlyBudget m set m.carriedInAmount = m.carriedInAmount + :amount
      where m.budgetYear = :year and m.budgetMonth = :month
      """)
  int transferIn(
      @Param("year") int year, @Param("month") int month, @Param("amount") long amount);

  /** 이미 지난 달 중 아직 마감하지 않은 월 예산의 ID 를 오래된 달부터 돌려준다. {@code year}·{@code month} 는 이번 달이다. */
  @Query(
      """
      select m.id from CashbackMonthlyBudget m
      where m.status <> :closed
        and (m.budgetYear < :year or (m.budgetYear = :year and m.budgetMonth < :month))
      order by m.budgetYear, m.budgetMonth
      """)
  List<Long> findClosableIds(
      @Param("year") int year, @Param("month") int month, @Param("closed") BudgetStatus closed);

  /**
   * 아직 마감하지 않은 월 예산만 마감({@code CLOSED})으로 바꾼다. 바꾸면 1, 이미 마감됐으면 0 을 돌려준다. 상태를 확인하고 바꾸는 것이 한 문장이라
   * 같은 달을 두 곳에서 마감해도 한쪽만 성공한다. 영속성 컨텍스트를 비우지 않는다.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackMonthlyBudget m set m.status = :closed
      where m.id = :id and m.status <> :closed
      """)
  int closeIfOpen(@Param("id") Long id, @Param("closed") BudgetStatus closed);

  /** 남은 예산(배정 + 이월 유입 - 이월 유출 - 예약 - 지급). 엔티티가 아니라 값만 읽어서 낡은 엔티티의 영향을 받지 않는다. */
  @Query(
      """
      select m.allocatedAmount + m.carriedInAmount - m.carriedOutAmount
             - m.reservedAmount - m.paidAmount
      from CashbackMonthlyBudget m where m.id = :id
      """)
  Optional<Long> findRemainingAmount(@Param("id") Long id);
}
