package com.back.nbe1214finalhot6.domain.cashback.entity;

/** 지급 배치가 적립 한 건을 처리한 결과. */
public enum PayoutItemResult {
  /** 지갑에 입금하고 지급 완료로 바꿨다. */
  PAID,
  /** 지급하지 않았다. 고르는 사이에 취소됐거나 다른 배치가 먼저 지급했다. */
  SKIPPED,
  /** 처리 중 오류가 나서 적립 예정으로 남겼다. */
  FAILED
}
