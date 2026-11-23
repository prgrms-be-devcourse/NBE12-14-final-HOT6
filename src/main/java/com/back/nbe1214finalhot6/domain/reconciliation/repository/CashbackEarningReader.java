package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 대사가 캐시백 적립 건을 읽기만 하는 저장소. */
public interface CashbackEarningReader extends Repository<CashbackEarning, Long> {

  List<CashbackEarning> findByIdIn(Collection<Long> ids);

  // 기간 안에 지급되었거나 취소·회수된 건
  @Query(
      """
      SELECT e.id FROM CashbackEarning e
      WHERE (e.paidAt >= :start AND e.paidAt < :end)
         OR (e.canceledAt >= :start AND e.canceledAt < :end)
      """)
  List<Long> findIdsChangedBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
