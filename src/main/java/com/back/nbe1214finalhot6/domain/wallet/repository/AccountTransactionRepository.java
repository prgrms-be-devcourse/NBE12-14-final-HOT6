package com.back.nbe1214finalhot6.domain.wallet.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

// 계좌 거래 내역 조회는 필요할 때 추가함
public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {}
