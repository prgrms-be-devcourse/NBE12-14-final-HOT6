package com.back.nbe1214finalhot6.domain.cashback.repository;

/** 월 예산의 현재 남은 예산과 전체 예산, 대비금. 상태 전이 이력의 남은 비율과 중단 공지의 기준 비율을 구할 때 쓴다. */
public interface MonthlyBudgetSnapshot {

  Long getId();

  /** 남은 예산(배정 + 이월 유입 - 예약 - 지급). */
  long getRemaining();

  /** 전체 예산(배정 + 이월 유입). */
  long getTotal();

  /** 대비금. 월 시작 시 확정한 값이다. */
  long getContingency();
}
