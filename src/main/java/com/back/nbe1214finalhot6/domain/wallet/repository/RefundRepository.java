package com.back.nbe1214finalhot6.domain.wallet.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.Refund;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {

  // 같은 지갑의 같은 멱등키로 이미 처리한 환불 조회(재요청 확인)
  Optional<Refund> findByWalletIdAndIdempotencyKey(Long walletId, String idempotencyKey);
}
