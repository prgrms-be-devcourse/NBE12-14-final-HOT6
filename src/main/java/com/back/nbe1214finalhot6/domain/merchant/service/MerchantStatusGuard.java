package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;

/** 가맹점 상태에 따른 거절 사유를 한곳에서 정한다. 영업 중이 아니면 상태에 맞는 오류를 던진다. */
public final class MerchantStatusGuard {

  private MerchantStatusGuard() {}

  public static void assertActive(MerchantStatus status) {
    switch (status) {
      case ACTIVE -> {}
      case CLOSING -> throw new BusinessException(MerchantErrorCode.MERCHANT_CLOSING);
      case CLOSED -> throw new BusinessException(MerchantErrorCode.MERCHANT_CLOSED);
    }
  }
}
