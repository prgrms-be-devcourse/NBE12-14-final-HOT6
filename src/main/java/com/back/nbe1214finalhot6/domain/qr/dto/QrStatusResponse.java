package com.back.nbe1214finalhot6.domain.qr.dto;

import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import com.back.nbe1214finalhot6.domain.qr.entity.QrStatus;
import java.time.LocalDateTime;

public record QrStatusResponse(QrStatus status, LocalDateTime expiresAt) {

  public static QrStatusResponse of(QrCode qrCode, LocalDateTime now) {
    return new QrStatusResponse(qrCode.statusAt(now), qrCode.getExpiresAt());
  }
}
