package com.back.nbe1214finalhot6.domain.settlement.repository;

import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DailySettlementRepository extends JpaRepository<DailySettlement, Long> {

  @Query("select max(s.settlementDate) from DailySettlement s")
  Optional<LocalDate> findLatestSettlementDate();

  boolean existsByMerchantIdAndSettlementDate(Long merchantId, LocalDate settlementDate);

  Optional<DailySettlement> findByMerchantIdAndSettlementDate(
      Long merchantId, LocalDate settlementDate);

  // 그날 다음 날로 부족분을 넘긴 가맹점. 다음 날 매출이 없어도 정산 대상에 넣는다.
  List<DailySettlement> findBySettlementDateAndCarryOutLessThan(
      LocalDate settlementDate, long carryOut);

  List<DailySettlement> findByMerchantIdAndSettlementDateBetweenOrderBySettlementDateAsc(
      Long merchantId, LocalDate from, LocalDate to);
}
