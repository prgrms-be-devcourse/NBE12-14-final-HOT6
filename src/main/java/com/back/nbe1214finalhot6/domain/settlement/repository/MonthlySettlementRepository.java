package com.back.nbe1214finalhot6.domain.settlement.repository;

import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlementStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MonthlySettlementRepository extends JpaRepository<MonthlySettlement, Long> {

  boolean existsByMerchantIdAndSettlementMonth(Long merchantId, LocalDate settlementMonth);

  // 달에 상관없이 아직 지급이 끝나지 않은 건. 오래된 건부터 재시도한다.
  List<MonthlySettlement> findByStatusOrderByIdAsc(MonthlySettlementStatus status);

  // 같은 건의 출금·지급 완료가 동시에 진행되지 않게 잠그고 읽음. 락 순서는 정산 건 → 지갑
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<MonthlySettlement> findWithLockById(Long id);
}
