package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 적립 건. 상태를 바꾸는 쿼리는 영속성 컨텍스트를 비우지 않는다. 결제 트랜잭션이 먼저 읽어 둔 엔티티(결제 등)가 분리되면 이후 변경이 저장되지 않기
 * 때문이다.
 */
public interface CashbackEarningRepository extends JpaRepository<CashbackEarning, Long> {

  /** 결제로 적립 건을 찾는다. {@code reserve} 가 같은 결제를 다시 받았는지 판단하는 멱등 키다. */
  Optional<CashbackEarning> findByPaymentId(Long paymentId);

  /**
   * 적립 예정({@code PENDING}) 건만 취소로 바꾼다. 바꾸면 1, 이미 다른 상태면 0 을 돌려준다. 상태를 확인하고 바꾸는 것이 한 문장이라, 지급 배치와
   * 동시에 와도 한쪽만 성공한다.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackEarning e set e.status = :canceled, e.canceledAt = :now
      where e.id = :id and e.status = :pending
      """)
  int cancelIfPending(
      @Param("id") Long id,
      @Param("now") LocalDateTime now,
      @Param("pending") EarningStatus pending,
      @Param("canceled") EarningStatus canceled);

  /** 현재 상태. 엔티티가 아니라 값만 읽어서 컨텍스트에 남은 낡은 엔티티의 영향을 받지 않는다. */
  @Query("select e.status from CashbackEarning e where e.id = :id")
  Optional<EarningStatus> findStatus(@Param("id") Long id);
  /**
   * 지급 대상 적립 건의 ID. 적립 예정이고 지급 예정 시각이 지난 건을 ID 순으로 {@code afterId} 이후부터 돌려준다. 지급에 실패한 건이 같은 배치에서
   * 계속 다시 잡히지 않도록 마지막으로 처리한 ID 를 넘겨 이어서 읽는다.
   */
  @Query(
      """
      select e.id from CashbackEarning e
      where e.status = :pending and e.payableAt <= :now and e.id > :afterId
      order by e.id
      """)
  List<Long> findPayableIds(
      @Param("now") LocalDateTime now,
      @Param("afterId") Long afterId,
      @Param("pending") EarningStatus pending,
      Pageable pageable);

  /**
   * 적립 예정({@code PENDING}) 건만 지급 완료로 바꾼다. 바꾸면 1, 이미 취소·지급됐으면 0 을 돌려준다. 상태를 확인하고 바꾸는 것이 한 문장이라
   * 취소와 동시에 와도 한쪽만 성공한다.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackEarning e set e.status = :paid, e.paidAt = :now
      where e.id = :id and e.status = :pending and e.payableAt <= :now
      """)
  int markPaidIfPending(
      @Param("id") Long id,
      @Param("now") LocalDateTime now,
      @Param("pending") EarningStatus pending,
      @Param("paid") EarningStatus paid);

  /** 지급 완료({@code PAID}) 건만 회수로 바꾼다. 바꾸면 1, 이미 다른 상태면 0 을 돌려준다. */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update CashbackEarning e set e.status = :clawedBack, e.canceledAt = :now
      where e.id = :id and e.status = :paid
      """)
  int clawBackIfPaid(
      @Param("id") Long id,
      @Param("now") LocalDateTime now,
      @Param("paid") EarningStatus paid,
      @Param("clawedBack") EarningStatus clawedBack);

  /** 한 이용자(지갑)의 적립 내역. 정렬과 페이지는 호출한 쪽이 정한다. */
  Page<CashbackEarning> findByWalletId(Long walletId, Pageable pageable);

  /** 한 이용자의 적립 내역 중 해당 상태만. */
  Page<CashbackEarning> findByWalletIdAndStatus(
      Long walletId, EarningStatus status, Pageable pageable);

  /**
   * 한 이용자의 적립 건을 상태별로 묶어 건수와 금액 합계를 돌려준다. 접수 시각이 {@code from} 이상 {@code to} 미만인 건만 센다. 읽기만 한다.
   */
  @Query(
      """
      select e.status as status, count(e) as count, coalesce(sum(e.amount), 0L) as amount
      from CashbackEarning e
      where e.walletId = :walletId and e.requestedAt >= :from and e.requestedAt < :to
      group by e.status
      """)
  List<EarningStatusTotal> summarizeByStatus(
      @Param("walletId") Long walletId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
