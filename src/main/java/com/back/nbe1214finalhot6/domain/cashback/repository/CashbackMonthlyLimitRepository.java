package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyLimit;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 이용자 월 적립 한도 사용량. 값을 바꾸는 쿼리는 영속성 컨텍스트를 비우지 않는다. 결제 트랜잭션이 먼저 읽어 둔 엔티티(결제 등)가 분리되면 이후 변경이
 * 저장되지 않기 때문이다.
 */
public interface CashbackMonthlyLimitRepository extends JpaRepository<CashbackMonthlyLimit, Long> {

  /** 사용량. 엔티티가 아니라 값만 읽어서 컨텍스트에 남은 낡은 엔티티의 영향을 받지 않는다. 행이 없으면 비어 있다. */
  @Query(
      "select l.usedAmount from CashbackMonthlyLimit l"
          + " where l.walletId = :walletId and l.limitMonth = :month")
  Optional<Long> findUsedAmount(@Param("walletId") Long walletId, @Param("month") LocalDate month);

  /**
   * 사용량이 없는 행을 만든다. 이미 있으면(동시에 만들었어도) 아무것도 하지 않는다. 같은 행을 동시에 처음 만들 때 한쪽이 유일 제약으로 실패하지 않도록
   * PostgreSQL 의 {@code ON CONFLICT DO NOTHING} 을 쓴다.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "insert into cashback_monthly_limit (wallet_id, limit_month, used_amount)"
              + " values (:walletId, :month, 0)"
              + " on conflict (wallet_id, limit_month) do nothing",
      nativeQuery = true)
  int insertIfAbsent(@Param("walletId") Long walletId, @Param("month") LocalDate month);

  /**
   * 사용량 + 금액이 한도 이하일 때만 사용량을 늘린다. 한 문장의 조건부 UPDATE 라서 읽고 판단하는 사이에 다른 결제가 끼어들 수 없다. 늘리면 1,
   * 한도를 넘거나 행이 없으면 0 을 돌려준다.
   *
   * <p>합이 아니라 {@code 한도 - 사용량} 과 비교해서 큰 값에서도 넘치지 않는다. 한도를 사용량보다 낮춘 경우는 음수가 되어 늘어나지 않는다.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackMonthlyLimit l set l.usedAmount = l.usedAmount + :amount
      where l.walletId = :walletId and l.limitMonth = :month
        and :limit - l.usedAmount >= :amount
      """)
  int consume(
      @Param("walletId") Long walletId,
      @Param("month") LocalDate month,
      @Param("amount") long amount,
      @Param("limit") long limit);

  /** 사용량을 되돌린다. 사용량보다 많이 빼야 하면 0 을 돌려주고 바꾸지 않는다. */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackMonthlyLimit l set l.usedAmount = l.usedAmount - :amount
      where l.walletId = :walletId and l.limitMonth = :month and l.usedAmount >= :amount
      """)
  int release(
      @Param("walletId") Long walletId,
      @Param("month") LocalDate month,
      @Param("amount") long amount);
}
