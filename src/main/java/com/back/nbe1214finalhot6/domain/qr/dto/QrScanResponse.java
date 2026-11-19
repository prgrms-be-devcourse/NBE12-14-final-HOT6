package com.back.nbe1214finalhot6.domain.qr.dto;

import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import java.time.LocalDateTime;

public record QrScanResponse(
    Long qrId,
    Long merchantId,
    String merchantName,
    Long amount,
    boolean cashbackEligible,
    LocalDateTime expiresAt) {

  public static QrScanResponse of(QrCode qrCode, Merchant merchant) {
    return new QrScanResponse(
        qrCode.getId(),
        merchant.getId(),
        merchant.getName(),
        qrCode.getAmount(),
        merchant.isCashbackEligible(),
        qrCode.getExpiresAt());
  }
}
