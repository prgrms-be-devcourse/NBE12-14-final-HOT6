package com.back.nbe1214finalhot6.domain.merchant.entity;

/** 가맹점 상태. 영업 중(ACTIVE)에서 해지 진행(CLOSING)을 거쳐 해지 완료(CLOSED)로만 바뀐다. */
public enum MerchantStatus {
  ACTIVE,
  CLOSING,
  CLOSED
}
