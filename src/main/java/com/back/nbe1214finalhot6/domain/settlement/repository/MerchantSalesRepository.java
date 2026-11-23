package com.back.nbe1214finalhot6.domain.settlement.repository;

import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.settlement.dto.MerchantSales;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 정산이 결제를 읽기만 하는 집계 쿼리. 구간은 start 이상, end 미만이다. */
public interface MerchantSalesRepository extends Repository<Payment, Long> {

  @Query(
      "select new com.back.nbe1214finalhot6.domain.settlement.dto.MerchantSales("
          + "p.merchantId, sum(p.amount)) from Payment p "
          + "where p.completedAt >= :start and p.completedAt < :end and p.status in :statuses "
          + "group by p.merchantId")
  List<MerchantSales> sumByCompletedAt(
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      @Param("statuses") Collection<PaymentStatus> statuses);

  @Query(
      "select new com.back.nbe1214finalhot6.domain.settlement.dto.MerchantSales("
          + "p.merchantId, sum(p.amount)) from Payment p "
          + "where p.canceledAt >= :start and p.canceledAt < :end and p.status = :status "
          + "group by p.merchantId")
  List<MerchantSales> sumByCanceledAt(
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      @Param("status") PaymentStatus status);
}
