package com.back.nbe1214finalhot6.domain.settlement.repository;

import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlementStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonthlySettlementRepository extends JpaRepository<MonthlySettlement, Long> {

  boolean existsByMerchantIdAndSettlementMonth(Long merchantId, LocalDate settlementMonth);

  // 달에 상관없이 아직 지급이 끝나지 않은 건. 오래된 건부터 재시도한다.
  List<MonthlySettlement> findByStatusOrderByIdAsc(MonthlySettlementStatus status);

  // 가맹점의 가장 최근 월 정산. 이 달 이후의 일 정산이 아직 월 정산에 묶이지 않은 매출이다.
  Optional<MonthlySettlement> findFirstByMerchantIdOrderBySettlementMonthDesc(Long merchantId);

  // 한 가맹점의 상태별 월 정산 건. 오래된 건부터 읽는다.
  List<MonthlySettlement> findByMerchantIdAndStatusOrderByIdAsc(
      Long merchantId, MonthlySettlementStatus status);

  // 가맹점의 지급 상태별 월 정산 합계(지급 대기 금액을 구할 때 쓴다).
  @Query(
      "select coalesce(sum(m.payoutAmount), 0) from MonthlySettlement m "
          + "where m.merchantId = :merchantId and m.status = :status")
  long sumPayoutAmountByStatus(
      @Param("merchantId") Long merchantId, @Param("status") MonthlySettlementStatus status);

  // 한 가맹점의 기간 안 월 정산 건. 양 끝 달을 포함한다.
  List<MonthlySettlement> findByMerchantIdAndSettlementMonthBetweenOrderBySettlementMonthAsc(
      Long merchantId, LocalDate from, LocalDate to);

  // 같은 건의 출금·지급 완료가 동시에 진행되지 않게 잠그고 읽음. 락 순서는 정산 건 → 지갑
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<MonthlySettlement> findWithLockById(Long id);
}
