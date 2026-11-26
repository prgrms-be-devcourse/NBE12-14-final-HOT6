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

  // 가장 오래된 정산일. 이 날짜보다 앞 기간은 일 정산이 만들어지지 않았다.
  @Query("select min(s.settlementDate) from DailySettlement s")
  Optional<LocalDate> findEarliestSettlementDate();

  boolean existsByMerchantIdAndSettlementDate(Long merchantId, LocalDate settlementDate);

  Optional<DailySettlement> findByMerchantIdAndSettlementDate(
      Long merchantId, LocalDate settlementDate);

  // 그날 다음 날로 부족분을 넘긴 가맹점. 다음 날 매출이 없어도 정산 대상에 넣는다.
  List<DailySettlement> findBySettlementDateAndCarryOutLessThan(
      LocalDate settlementDate, long carryOut);

  List<DailySettlement> findByMerchantIdAndSettlementDateBetweenOrderBySettlementDateAsc(
      Long merchantId, LocalDate from, LocalDate to);

  // 가맹점의 가장 최근 일 정산. 마지막 날의 이월 부족분(carryOut)을 읽을 때 쓴다.
  Optional<DailySettlement> findFirstByMerchantIdOrderBySettlementDateDesc(Long merchantId);

  // from 이상의 확정액 합계. 아직 월 정산에 묶이지 않은 매출을 구할 때 쓴다.
  @Query(
      "select coalesce(sum(s.confirmedAmount), 0) from DailySettlement s "
          + "where s.merchantId = :merchantId and s.settlementDate >= :from")
  long sumConfirmedAmountFrom(
      @Param("merchantId") Long merchantId, @Param("from") LocalDate from);

  // 가맹점의 가장 오래된 일 정산. 월 정산에 묶이지 않은 첫 달을 찾을 때 쓴다.
  Optional<DailySettlement> findFirstByMerchantIdOrderBySettlementDateAsc(Long merchantId);

  // 한 가맹점의 from 이상 to 미만 기간의 확정액 합계.
  @Query(
      "select coalesce(sum(s.confirmedAmount), 0) from DailySettlement s "
          + "where s.merchantId = :merchantId "
          + "and s.settlementDate >= :from and s.settlementDate < :to")
  long sumConfirmedAmountByMerchantBetween(
      @Param("merchantId") Long merchantId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);

  // from 이상 to 미만 기간의 확정액을 가맹점별로 합산한다. 해지를 신청한 가맹점은 해지 정산이 따로 처리하므로 뺀다.
  @Query(
      "select new com.back.nbe1214finalhot6.domain.settlement.dto.MerchantSales("
          + "s.merchantId, sum(s.confirmedAmount)) "
          + "from DailySettlement s "
          + "where s.settlementDate >= :from and s.settlementDate < :to "
          + "and s.merchantId not in (select m.id from Merchant m "
          + "where m.status <> com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus.ACTIVE) "
          + "group by s.merchantId")
  List<MerchantSales> sumConfirmedAmountBetween(
      @Param("from") LocalDate from, @Param("to") LocalDate to);
}
