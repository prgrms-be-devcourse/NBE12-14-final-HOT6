package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackAnnualBudget;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashbackAnnualBudgetRepository extends JpaRepository<CashbackAnnualBudget, Long> {

  Optional<CashbackAnnualBudget> findByBudgetYear(int budgetYear);

  boolean existsByBudgetYear(int budgetYear);

  /**
   * 남은 예산이 금액 이상일 때만 확보 금액을 늘린다. 한 문장의 조건부 UPDATE 라서 읽고 판단하는 사이에 다른 결제가 끼어들 수 없다.
   * 확보하면 1, 모자라거나 예산이 없으면 0 을 돌려준다.
   *
   * <p>영속성 컨텍스트를 비우지 않는다. 결제 트랜잭션이 먼저 읽어 둔 엔티티(결제 등)가 분리되면 이후 변경이 저장되지 않기 때문이다. 대신 같은
   * 트랜잭션에서 예산 엔티티를 먼저 읽어 두었다면 갱신 전 값일 수 있다.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackAnnualBudget a set a.reservedAmount = a.reservedAmount + :amount
      where a.budgetYear = :year
        and a.totalAmount + a.carriedInAmount - a.carriedOutAmount - a.reservedAmount - a.paidAmount
            >= :amount
      """)
  int reserve(@Param("year") int year, @Param("amount") long amount);

  /** 확보한 금액을 되돌린다. 확보한 금액보다 많이 빼야 하면 0 을 돌려주고 바꾸지 않는다. */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackAnnualBudget a set a.reservedAmount = a.reservedAmount - :amount
      where a.budgetYear = :year and a.reservedAmount >= :amount
      """)
  int release(@Param("year") int year, @Param("amount") long amount);

  /**
   * 확보했던 금액을 지급 금액으로 옮긴다(확보 - 금액, 지급 + 금액). 남은 예산은 변하지 않는다. 확보한 금액보다 많이 옮겨야 하면 0 을 돌려주고 바꾸지
   * 않는다. 영속성 컨텍스트를 비우지 않는다(확보 쪽 설명 참고).
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackAnnualBudget a
      set a.reservedAmount = a.reservedAmount - :amount, a.paidAmount = a.paidAmount + :amount
      where a.budgetYear = :year and a.reservedAmount >= :amount
      """)
  int settle(@Param("year") int year, @Param("amount") long amount);

  /**
   * 남은 예산이 금액 이상일 때만 다른 해로 넘긴 금액을 늘린다(연말 이월). 넘기면 1, 모자라거나 예산이 없으면 0 을 돌려준다. 영속성 컨텍스트를 비우지 않는다.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackAnnualBudget a set a.carriedOutAmount = a.carriedOutAmount + :amount
      where a.budgetYear = :year
        and a.totalAmount + a.carriedInAmount - a.carriedOutAmount - a.reservedAmount - a.paidAmount
            >= :amount
      """)
  int transferOut(@Param("year") int year, @Param("amount") long amount);

  /** 다른 해에서 넘어온 금액을 늘린다(연말 이월). 늘리면 1, 예산이 없으면 0 을 돌려준다. */
  @Modifying(flushAutomatically = true)
  @Query(
      "update CashbackAnnualBudget a set a.carriedInAmount = a.carriedInAmount + :amount"
          + " where a.budgetYear = :year")
  int transferIn(@Param("year") int year, @Param("amount") long amount);

  /**
   * 행을 잠그고 읽는다. 총액 변경이나 월 배정 합계 검증 중에 결제의 예약 금액 변경이 끼어들어 덮어써지지 않게 한다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<CashbackAnnualBudget> findWithLockByBudgetYear(int budgetYear);
}
