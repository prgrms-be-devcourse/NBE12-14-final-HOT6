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

  /** 지갑 행을 잠그고 읽음. 결제 락 순서(결제 → QR → 지갑 → 예산)의 '지갑' 단계. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM Wallet w WHERE w.id = :id")
  Optional<Wallet> findByIdForUpdate(@Param("id") Long id);

  /**
   * 데브머니 잔액만 값으로 읽음. 같은 트랜잭션에서 이미 읽어 둔 Wallet 엔티티(낡았을 수 있음)를 거치지 않고 DB의 현재 값을 읽음.
   * 같은 트랜잭션에서 아직 저장하지 않은 변경은 이 조회 전에 자동으로 반영됨.
   */
  @Query("SELECT w.balance FROM Wallet w WHERE w.id = :id")
  Optional<Long> findBalanceById(@Param("id") Long id);
}
