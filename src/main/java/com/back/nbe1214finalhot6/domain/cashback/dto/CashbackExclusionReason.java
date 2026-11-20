package com.back.nbe1214finalhot6.domain.cashback.dto;

/**
 * 적립이 0원이 된 이유. 영수증과 적립 내역에 그대로 보여 준다.
 *
 * <p>한 건에 하나만 기록하며, 아래 순서로 먼저 걸리는 사유를 쓴다: 적립 대상 아님, 데브머니 사용분 없음, 적립액 1원 미만, 월 한도 초과, 예산 소진.
 */
public enum CashbackExclusionReason {
  /** 적립됐다(제외 아님). */
  NONE,
  /** 적립 대상 가맹점이 아니다. */
  NOT_ELIGIBLE,
  /** 데브머니로 결제한 금액이 없다(전액 캐시백으로 결제). */
  NO_WALLET_USAGE,
  /** 소수점 이하를 버리면 적립액이 0원이다. */
  AMOUNT_TOO_SMALL,
  /** 이용자의 이번 달 적립 한도를 넘는다. */
  MONTHLY_LIMIT_EXCEEDED,
  /** 이번 달 캐시백 예산이 소진됐거나 없거나 적립이 중단됐다. */
  BUDGET_EXHAUSTED
}
