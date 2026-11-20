package com.back.nbe1214finalhot6.domain.cashback.service;

/**
 * 예산 확보 결과. 예산이 모자라는 것은 예외가 아니라 정상적인 결과이며, 결제는 그대로 진행된다.
 */
public enum BudgetReserveResult {
  /** 연·월 예산에서 금액을 확보했다. */
  RESERVED,
  /** 해당 연도나 월의 예산이 없다. */
  NO_BUDGET,
  /** 월 예산이 적립 중단·마감 상태라 확보하지 않았다. */
  NOT_ACTIVE,
  /** 남은 예산이 모자라 확보하지 않았다. 부분 확보는 없다. */
  INSUFFICIENT;

  public boolean isReserved() {
    return this == RESERVED;
  }
}
