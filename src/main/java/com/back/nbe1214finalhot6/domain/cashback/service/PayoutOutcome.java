package com.back.nbe1214finalhot6.domain.cashback.service;

/** 적립 한 건의 지급 처리 결과. */
public enum PayoutOutcome {
  /** 지갑에 입금하고 지급 완료로 바꿨다. */
  PAID,
  /** 지급하지 않았다. 지급 대상이 아니거나, 그 사이에 취소됐거나, 다른 배치가 먼저 지급했다. */
  SKIPPED
}
