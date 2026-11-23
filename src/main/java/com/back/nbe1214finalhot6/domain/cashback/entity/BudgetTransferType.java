package com.back.nbe1214finalhot6.domain.cashback.entity;

/** 예산 이월의 종류. */
public enum BudgetTransferType {
  /** 운영자가 직접 이월했다. */
  MANUAL,
  /** 월 경계에서 시스템이 자동으로 이월했다. 아직 쓰지 않는다. */
  AUTO
}
