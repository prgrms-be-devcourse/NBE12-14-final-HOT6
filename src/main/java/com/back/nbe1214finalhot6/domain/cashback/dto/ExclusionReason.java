package com.back.nbe1214finalhot6.domain.cashback.dto;

/** 적립이 0원이 된 이유. 영수증과 적립 내역에 그대로 보여 준다. */
public enum ExclusionReason {
  /** 적립됐다(제외 아님). */
  NONE,
  /** 적립 대상 가맹점이 아니다. */
  NOT_ELIGIBLE,
  /** 이번 달 캐시백 예산이 소진됐거나 적립이 중단됐다. */
  BUDGET_EXHAUSTED,
  /** 이용자의 이번 달 적립 한도를 넘는다. */
  MONTHLY_LIMIT_EXCEEDED
}
