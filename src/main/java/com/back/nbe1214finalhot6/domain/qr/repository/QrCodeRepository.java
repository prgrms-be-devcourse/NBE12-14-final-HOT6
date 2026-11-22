package com.back.nbe1214finalhot6.domain.qr.repository;

import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import com.back.nbe1214finalhot6.domain.qr.entity.QrStatus;
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
         and q.status = com.back.nbe1214finalhot6.domain.qr.entity.QrStatus.WAITING
         and q.expiresAt > :requestedAt
      """)
  int markUsedIfAvailable(
      @Param("token") String token,
      @Param("requestedAt") LocalDateTime requestedAt,
      @Param("usedAt") LocalDateTime usedAt);

  @Query("select q.status from QrCode q where q.token = :token")
  Optional<QrStatus> findStatusByToken(@Param("token") String token);
}
