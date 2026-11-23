package com.back.nbe1214finalhot6.domain.settlement.repository;

import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlementStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlySettlementRepository extends JpaRepository<MonthlySettlement, Long> {

  boolean existsByMerchantIdAndSettlementMonth(Long merchantId, LocalDate settlementMonth);

  // 달에 상관없이 아직 지급이 끝나지 않은 건. 오래된 건부터 재시도한다.
  List<MonthlySettlement> findByStatusOrderByIdAsc(MonthlySettlementStatus status);
}
