package com.back.nbe1214finalhot6.domain.wallet.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

  // 회원의 계좌 조회
  Optional<Account> findByUserId(Long userId);

  /**
   * 회원의 계좌 행을 잠그고 읽음. 충전·환불·탈퇴 정산은 지갑 → 계좌 순서로 잠금.
   * 같은 트랜잭션에서 이 계좌를 먼저 읽었다면 낡은 객체가 돌아올 수 있으므로, 잠그기 전에 계좌를 읽지 말 것.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM Account a WHERE a.userId = :userId")
  Optional<Account> findByUserIdForUpdate(@Param("userId") Long userId);
}
