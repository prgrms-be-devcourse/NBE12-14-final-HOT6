package com.back.nbe1214finalhot6.domain.merchant.dto;

import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;

public record MerchantResponse(
    Long merchantId,
    String name,
    String address,
    boolean cashbackEligible,
    MerchantStatus status,
    Double latitude,
    Double longitude) {

  public static MerchantResponse from(Merchant merchant) {
    return new MerchantResponse(
        merchant.getId(),
        merchant.getName(),
        merchant.getAddress(),
        merchant.isCashbackEligible(),
        merchant.getStatus(),
        merchant.getLatitude(),
        merchant.getLongitude());
  }
}
