package com.back.nbe1214finalhot6.domain.cashback.service;

/** 월 예산 한 건의 마감 처리 결과. */
public enum BudgetCloseOutcome {
  /** 마감했다. 남은 예산이 있었다면 다음 달로 이월했다. */
  CLOSED,
  /** 마감하지 않았다. 다음 달 월 예산(12월이면 다음 해 연 예산도)이 없어서 보류했거나, 다른 곳에서 먼저 마감했다. */
  SKIPPED
}
