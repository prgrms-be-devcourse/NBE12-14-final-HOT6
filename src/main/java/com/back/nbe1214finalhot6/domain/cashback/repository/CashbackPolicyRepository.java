package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashbackPolicyRepository extends JpaRepository<CashbackPolicy, Long> {

  /** 무기한(종료 시각 없음)을 비교할 때 쓰는 먼 미래 시각. PostgreSQL 이 null 파라미터의 타입을 추론하지 못해서 null 대신 쓴다. */
  LocalDateTime FAR_FUTURE = LocalDateTime.of(9999, 12, 31, 0, 0);

  /**
   * {@code [startsAt, endsAt)} 와 기간이 겹치는 정책이 있는지 확인한다. {@code endsAt} 이 null 이면 무기한으로 본다. 경계가 맞닿는 경우(한쪽 종료
   * = 다른 쪽 시작)는 겹치지 않는다.
   */
  default boolean existsOverlapping(LocalDateTime startsAt, LocalDateTime endsAt) {
    return existsOverlapping(startsAt, endsAt == null ? FAR_FUTURE : endsAt, FAR_FUTURE);
  }

  @Query(
      """
      select count(p) > 0 from CashbackPolicy p
      where p.startsAt < :endsAt
        and coalesce(p.endsAt, :farFuture) > :startsAt
      """)
  boolean existsOverlapping(
      @Param("startsAt") LocalDateTime startsAt,
      @Param("endsAt") LocalDateTime endsAt,
      @Param("farFuture") LocalDateTime farFuture);

  /** 해당 시각에 유효한 정책. 기간이 겹치지 않으므로 보통 하나이고, 방어적으로 시작이 늦은 순으로 돌려준다. */
  @Query(
      """
      select p from CashbackPolicy p
      where p.startsAt <= :at and (p.endsAt is null or p.endsAt > :at)
      order by p.startsAt desc
      """)
  List<CashbackPolicy> findEffective(@Param("at") LocalDateTime at);

  List<CashbackPolicy> findAllByOrderByStartsAtDesc();

  /** {@code [from, to)} 안에서 시작한 구간 수. 그 달의 요율 인하 횟수를 셀 때 쓴다. */
  long countByStartsAtGreaterThanEqualAndStartsAtLessThan(LocalDateTime from, LocalDateTime to);

  /** 종료 시각이 주어진 시각 중 하나인 요율 구간. 인하가 직전 구간을 적용 시각에 끝내므로, 인하 구간의 직전 구간을 찾는 데 쓴다. 읽기만 한다. */
  List<CashbackPolicy> findByEndsAtIn(Collection<LocalDateTime> endsAt);
}
