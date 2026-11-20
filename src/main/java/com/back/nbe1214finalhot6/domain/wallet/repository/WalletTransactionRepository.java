package com.back.nbe1214finalhot6.domain.wallet.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

// 락 걸린 조회와 거래 내역 조회는 W1·W5에서 추가함
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {}
