package com.back.nbe1214finalhot6.domain.qr.repository;

import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QrCodeRepository extends JpaRepository<QrCode, Long> {

  Optional<QrCode> findByToken(String token);
}
