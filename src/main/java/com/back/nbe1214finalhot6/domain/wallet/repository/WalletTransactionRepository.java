package com.back.nbe1214finalhot6.domain.wallet.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

  // 같은 (type, refKey)가 이미 반영됐는지 확인(재요청 방지)
  boolean existsByTypeAndRefKey(WalletTransactionType type, Long refKey);

  // 같은 (type, refKey)의 거래 조회. 결제 취소 환급이 원래 결제 차감을 확인할 때 사용
  Optional<WalletTransaction> findByTypeAndRefKey(WalletTransactionType type, Long refKey);

  // 기간 [periodStart, periodEnd) 동안 지갑의 특정 type 거래 금액 합계(없으면 0).
  // 월 충전 한도의 이번 달 누적 충전액 계산에 사용
  @Query(
      """
      SELECT COALESCE(SUM(t.amount), 0) FROM WalletTransaction t
      WHERE t.walletId = :walletId AND t.type = :type
        AND t.createdAt >= :periodStart AND t.createdAt < :periodEnd
      """)
  long sumAmount(
      @Param("walletId") Long walletId,
      @Param("type") WalletTransactionType type,
      @Param("periodStart") LocalDateTime periodStart,
      @Param("periodEnd") LocalDateTime periodEnd);

  // 지갑의 거래 내역. 정렬은 호출한 쪽의 Pageable 로 정한다(최신순: createdAt, id 내림차순)
  Page<WalletTransaction> findByWalletId(Long walletId, Pageable pageable);
}
