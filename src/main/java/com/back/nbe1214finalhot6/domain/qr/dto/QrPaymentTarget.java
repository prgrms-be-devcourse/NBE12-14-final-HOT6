package com.back.nbe1214finalhot6.domain.qr.dto;

import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;

public record QrPaymentTarget(Long merchantId, Long amount) {

  public static QrPaymentTarget from(QrCode qrCode) {
    return new QrPaymentTarget(qrCode.getMerchantId(), qrCode.getAmount());
  }
}
