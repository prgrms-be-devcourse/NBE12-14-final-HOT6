package com.back.nbe1214finalhot6.domain.merchant.dto;

import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;

public record MerchantRegisterResponse(
    Long merchantId,
    String name,
    String address,
    boolean cashbackEligible,
    Double latitude,
    Double longitude) {

  public static MerchantRegisterResponse from(Merchant merchant) {
    return new MerchantRegisterResponse(
        merchant.getId(),
        merchant.getName(),
        merchant.getAddress(),
        merchant.isCashbackEligible(),
        merchant.getLatitude(),
        merchant.getLongitude());
  }
}
