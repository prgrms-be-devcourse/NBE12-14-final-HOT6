package com.back.nbe1214finalhot6.domain.wallet.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {

  // 계좌의 거래 내역. 정렬은 호출한 쪽의 Pageable 로 정함(최신순: createdAt, id 내림차순)
  Page<AccountTransaction> findByAccountId(Long accountId, Pageable pageable);
}
