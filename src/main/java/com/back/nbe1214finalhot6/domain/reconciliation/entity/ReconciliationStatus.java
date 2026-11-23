package com.back.nbe1214finalhot6.domain.reconciliation.entity;

public enum ReconciliationStatus {
  MATCHED,
  MISMATCHED,
  // 검사 자체가 실패한 경우. 불일치와 구분한다.
  ERROR
}
