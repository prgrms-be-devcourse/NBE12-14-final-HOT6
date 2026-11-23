package com.back.nbe1214finalhot6.domain.reconciliation.entity;

public enum ReconciliationType {
  // 6번: 당일 순매출 = 결제 - 취소
  DAILY_NET_SALES,
  // 7번: 정산 확정액 + 다음 날 이월 = 순매출 + 전일 이월
  SETTLEMENT_CARRY,
  // 월 지급액 = 일 정산 확정액 합 = 지갑 원장 입금·출금
  MONTHLY_PAYOUT
}
