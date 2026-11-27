package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicyHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashbackPolicyHistoryRepository
    extends JpaRepository<CashbackPolicyHistory, Long> {

  /** 해당 구간의 변경 이력을 오래된 순으로 돌려준다. */
  List<CashbackPolicyHistory> findAllByPolicyIdOrderByIdAsc(Long policyId);

  /** 한 구간의 변경 이력. 정렬과 페이지는 호출한 쪽이 정한다. */
  Page<CashbackPolicyHistory> findAllByPolicyId(Long policyId, Pageable pageable);

  /** 해당 구간에 마지막으로 남긴 사유가 있는 이력. */
  Optional<CashbackPolicyHistory> findFirstByPolicyIdAndReasonIsNotNullOrderByIdDesc(Long policyId);
}
