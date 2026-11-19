package com.back.nbe1214finalhot6.domain.cashback.entity;

/** 월 예산의 적립 상태. 전이 규칙은 중단 로직에서 다룬다. */
public enum BudgetStatus {
  /** 정상 적립. 월 예산이 열릴 때의 기본값 */
  ACTIVE,
  /** 중단 공지 중. 적립은 계속한다 */
  NOTICED,
  /** 적립 중단. 결제는 정상이고 적립만 0원이다 */
  STOPPED,
  /** 마감된 달 */
  CLOSED
}
