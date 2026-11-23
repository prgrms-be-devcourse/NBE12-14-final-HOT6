package com.back.nbe1214finalhot6.domain.cashback.repository;

/** 월 예산의 현재 남은 예산과 전체 예산. 상태 전이 이력에 남은 비율을 남길 때 쓴다. */
public interface MonthlyBudgetSnapshot {

  Long getId();

  /** 남은 예산(배정 + 이월 유입 - 예약 - 지급). */
  long getRemaining();

  /** 전체 예산(배정 + 이월 유입). */
  long getTotal();
}
