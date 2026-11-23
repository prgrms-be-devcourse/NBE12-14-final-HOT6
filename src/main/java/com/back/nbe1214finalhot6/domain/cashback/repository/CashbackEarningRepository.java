package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import java.time.LocalDateTime;
import java.util.Optional;
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
}
