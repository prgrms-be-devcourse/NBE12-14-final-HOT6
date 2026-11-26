package com.back.nbe1214finalhot6.domain.payment.repository;

import com.back.nbe1214finalhot6.domain.payment.dto.SalesTotals;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  Optional<Payment> findByWalletIdAndIdempotencyKey(Long walletId, String idempotencyKey);

  // 사장님 결제 한 건 조회. 가맹점 번호까지 조건에 넣어 다른 가맹점의 결제는 조회되지 않는다
  Optional<Payment> findByIdAndMerchantId(Long id, Long merchantId);

  @Transactional //테스트 호출을 위한 어노테이션 처리
  @Modifying(clearAutomatically = true)
  @Query("UPDATE Payment p SET p.status = :to WHERE p.id = :id AND p.status = :from")
  int updateStatusIfCurrent(
      @Param("id") Long id, @Param("from") PaymentStatus from, @Param("to") PaymentStatus to);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Payment p WHERE p.id = :id")
  Optional<Payment> findByIdWithLock(@Param("id") Long id);

  // ---- 사장님 결제 내역 (구간은 start 이상, end 미만) ----

  // 기간 안에 완료한 결제의 건수와 총액. 일 정산과 같은 기준(완료 시각, 완료·취소 상태)이다
  @Query(
      "select new com.back.nbe1214finalhot6.domain.payment.dto.SalesTotals("
          + "count(p), coalesce(sum(p.amount), 0L)) from Payment p "
          + "where p.merchantId = :merchantId and p.completedAt >= :start and p.completedAt < :end "
          + "and p.status in :statuses")
  SalesTotals totalsByCompletedAt(
      @Param("merchantId") Long merchantId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      @Param("statuses") Collection<PaymentStatus> statuses);

  // 기간 안에 취소한 결제의 건수와 총액 (취소 시각 기준)
  @Query(
      "select new com.back.nbe1214finalhot6.domain.payment.dto.SalesTotals("
          + "count(p), coalesce(sum(p.amount), 0L)) from Payment p "
          + "where p.merchantId = :merchantId and p.canceledAt >= :start and p.canceledAt < :end "
          + "and p.status = :status")
  SalesTotals totalsByCanceledAt(
      @Param("merchantId") Long merchantId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      @Param("status") PaymentStatus status);

  // 기간 안에 완료했거나 취소한 결제 목록. 정렬은 호출한 쪽의 Pageable 로 정한다
  @Query(
      "select p from Payment p where p.merchantId = :merchantId and ("
          + "(p.completedAt >= :start and p.completedAt < :end and p.status in :statuses) "
          + "or (p.canceledAt >= :start and p.canceledAt < :end and p.status = :canceled))")
  Page<Payment> findSalesBetween(
      @Param("merchantId") Long merchantId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      @Param("statuses") Collection<PaymentStatus> statuses,
      @Param("canceled") PaymentStatus canceled,
      Pageable pageable);


  // ---- 이용자 결제 내역 ----

  // 내 지갑의 결제 목록. 상태 필터는 목록으로 받고, 정렬은 호출한 쪽의 Pageable 로 정한다
  Page<Payment> findByWalletIdAndStatusIn(
      Long walletId, Collection<PaymentStatus> statuses, Pageable pageable);
}
