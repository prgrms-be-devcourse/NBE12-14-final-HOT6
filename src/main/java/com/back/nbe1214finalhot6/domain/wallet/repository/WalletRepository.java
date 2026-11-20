package com.back.nbe1214finalhot6.domain.wallet.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

  // 회원의 지갑 조회
  Optional<Wallet> findByUserId(Long userId);

  /** 지갑 행을 잠그고 읽음. 결제 락 순서(결제 → 지갑 → QR → 예산)의 '지갑' 단계. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM Wallet w WHERE w.id = :id")
  Optional<Wallet> findByIdForUpdate(@Param("id") Long id);
}
