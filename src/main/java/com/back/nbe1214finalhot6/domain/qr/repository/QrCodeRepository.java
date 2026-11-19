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
         set q.status = com.back.nbe1214finalhot6.domain.qr.entity.QrStatus.USED
       where q.id = :id
         and q.status = com.back.nbe1214finalhot6.domain.qr.entity.QrStatus.WAITING
         and q.expiresAt > :now
      """)
  int markUsedIfAvailable(@Param("id") Long id, @Param("now") LocalDateTime now);

  @Query("select q.status from QrCode q where q.id = :id")
  Optional<QrStatus> findStatusById(@Param("id") Long id);
}
