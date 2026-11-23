package com.back.nbe1214finalhot6.domain.settlement.repository;

import com.back.nbe1214finalhot6.domain.settlement.dto.MerchantSales;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  // from 이상 to 미만 기간의 확정액을 가맹점별로 합산한다.
  @Query(
      "select new com.back.nbe1214finalhot6.domain.settlement.dto.MerchantSales("
          + "s.merchantId, sum(s.confirmedAmount)) "
          + "from DailySettlement s "
          + "where s.settlementDate >= :from and s.settlementDate < :to "
          + "group by s.merchantId")
  List<MerchantSales> sumConfirmedAmountBetween(
      @Param("from") LocalDate from, @Param("to") LocalDate to);
}
