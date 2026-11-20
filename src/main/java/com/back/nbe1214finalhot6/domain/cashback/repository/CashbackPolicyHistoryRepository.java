package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicyHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashbackPolicyHistoryRepository
    extends JpaRepository<CashbackPolicyHistory, Long> {

  /** 해당 구간의 변경 이력을 오래된 순으로 돌려준다. */
  List<CashbackPolicyHistory> findAllByPolicyIdOrderByIdAsc(Long policyId);
}
