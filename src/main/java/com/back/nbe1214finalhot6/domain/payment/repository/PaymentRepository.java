package com.back.nbe1214finalhot6.domain.payment.repository;

import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import java.util.Optional;


import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  Optional<Payment> findByWalletIdAndIdempotencyKey(Long walletId, String idempotencyKey);

  @Transactional //테스트 호출을 위한 어노테이션 처리
  @Modifying(clearAutomatically = true)
  @Query("UPDATE Payment p SET p.status = :to WHERE p.id = :id AND p.status = :from")
  int updateStatusIfCurrent(
      @Param("id") Long id, @Param("from") PaymentStatus from, @Param("to") PaymentStatus to);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Payment p WHERE p.id = :id")
  Optional<Payment> findByIdWithLock(@Param("id") Long id);
}
