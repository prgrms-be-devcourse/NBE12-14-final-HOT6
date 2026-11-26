package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPayoutRunItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashbackPayoutRunItemRepository
    extends JpaRepository<CashbackPayoutRunItem, Long> {

  /** 한 실행의 항목을 적립 건 순으로 돌려준다. */
  List<CashbackPayoutRunItem> findByRunIdOrderByEarningIdAsc(Long runId);
}
