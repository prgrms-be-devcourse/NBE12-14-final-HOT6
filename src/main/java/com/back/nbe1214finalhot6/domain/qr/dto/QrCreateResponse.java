package com.back.nbe1214finalhot6.domain.qr.dto;

import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import java.time.LocalDateTime;

public record QrCreateResponse(String token, Long amount, LocalDateTime expiresAt) {

  public static QrCreateResponse from(QrCode qrCode) {
    return new QrCreateResponse(qrCode.getToken(), qrCode.getAmount(), qrCode.getExpiresAt());
  }
}
