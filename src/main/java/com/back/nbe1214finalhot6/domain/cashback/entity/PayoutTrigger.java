package com.back.nbe1214finalhot6.domain.cashback.entity;

/** 지급 배치를 실행한 방식. */
public enum PayoutTrigger {
  /** 스케줄러가 주기적으로 실행했다. */
  SCHEDULED,
  /** 운영자가 직접 실행했다. */
  MANUAL
}
