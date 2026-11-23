package com.back.nbe1214finalhot6.domain.cashback.entity;

/** 월 예산의 대비금 경고 단계. 남은 예산이 대비금 기준선 아래로 내려갔는지를 조회할 때 계산하며 저장하지 않는다. */
public enum BudgetWarning {
  /** 경고 없음. 남은 예산이 대비금보다 많다. */
  NONE,
  /** 공지선. 남은 예산이 대비금 이하다. */
  NOTICE_LINE,
  /** 긴급선. 남은 예산이 대비금 × 긴급선 비율 이하다. */
  EMERGENCY_LINE
}
