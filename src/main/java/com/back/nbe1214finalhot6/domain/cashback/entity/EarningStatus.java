package com.back.nbe1214finalhot6.domain.cashback.entity;

/** 적립 건의 상태. */
public enum EarningStatus {
  /** 적립 예정. 예산을 확보했고 아직 지급 전이다. */
  PENDING,
  /** 지급 완료. */
  PAID,
  /** 지급 전에 결제가 취소되어 예산을 돌려줬다. */
  CANCELED,
  /** 지급 후에 결제가 취소되어 회수했다. */
  CLAWED_BACK,
  /** 적립하지 않은 건. 사유는 제외 사유에 남긴다. */
  EXCLUDED
}
