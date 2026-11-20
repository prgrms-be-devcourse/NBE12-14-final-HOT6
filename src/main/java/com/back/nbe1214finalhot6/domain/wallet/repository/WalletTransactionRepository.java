package com.back.nbe1214finalhot6.domain.wallet.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 거래 내역 조회는 W5에서 추가함
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

  // 같은 (type, refKey)가 이미 반영됐는지 확인(재요청 방지)
  boolean existsByTypeAndRefKey(WalletTransactionType type, Long refKey);

  // 같은 (type, refKey)의 거래 조회. 결제 취소 환급이 원래 결제 차감을 확인할 때 사용
  Optional<WalletTransaction> findByTypeAndRefKey(WalletTransactionType type, Long refKey);
}
