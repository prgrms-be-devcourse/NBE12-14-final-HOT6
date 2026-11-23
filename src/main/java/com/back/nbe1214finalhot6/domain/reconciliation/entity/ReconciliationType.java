package com.back.nbe1214finalhot6.domain.reconciliation.entity;

public enum ReconciliationType {
  // 1번: 계좌 잔액 = 계좌 원장 합계
  ACCOUNT_BALANCE,
  // 2번: 데브머니 잔액 = 데브머니 거래 원장 합계
  DEVMONEY_BALANCE,
  // 3번: 캐시백 잔액 = 캐시백 거래 원장 합계
  CASHBACK_BALANCE,
  // 3번: 적립 건 상태와 원장의 적립·회수 기록 일치
  CASHBACK_EARNING,
  // 3번: 캐시백 월·연 예산의 확보·지급 금액 = 적립 건 합계
  CASHBACK_BUDGET,
  // 4번: 충전액 = 계좌 원장 차감액 = 지갑 원장 입금액
  CHARGE_TRANSFER,
  // 5번: 환불액 = 지갑 원장 차감액 = 계좌 원장 입금액
  REFUND_TRANSFER,
  // 6번: 당일 순매출 = 결제 - 취소
  DAILY_NET_SALES,
  // 7번: 정산 확정액 + 다음 날 이월 = 순매출 + 전일 이월
  SETTLEMENT_CARRY,
  // 월 지급액 = 일 정산 확정액 합 = 지갑 원장 입금·출금
  MONTHLY_PAYOUT
}
