package com.back.nbe1214finalhot6.domain.qr.repository;

import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QrCodeRepository extends JpaRepository<QrCode, Long> {

  Optional<QrCode> findByToken(String token);

  @Modifying
  @Query(
      """
      update QrCode q
         set q.status = com.back.nbe1214finalhot6.domain.qr.entity.QrStatus.USED,
             q.usedAt = :usedAt
       where q.token = :token
         and q.merchantId = :merchantId
         and q.amount = :amount
         and q.status = com.back.nbe1214finalhot6.domain.qr.entity.QrStatus.WAITING
         and q.expiresAt > :requestedAt
      """)
  int markUsedIfMatches(
      @Param("token") String token,
      @Param("merchantId") Long merchantId,
      @Param("amount") Long amount,
      @Param("requestedAt") LocalDateTime requestedAt,
      @Param("usedAt") LocalDateTime usedAt);

  @Query(
      """
      select new com.back.nbe1214finalhot6.domain.qr.repository.QrSnapshot(
             q.merchantId, q.amount, q.status)
        from QrCode q
       where q.token = :token
      """)
  Optional<QrSnapshot> findSnapshotByToken(@Param("token") String token);
}
