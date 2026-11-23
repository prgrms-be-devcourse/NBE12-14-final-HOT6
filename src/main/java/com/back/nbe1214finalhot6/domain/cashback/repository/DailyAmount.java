package com.back.nbe1214finalhot6.domain.cashback.repository;

import java.time.LocalDate;

/** 날짜별 금액 합계. 일별 시계열 집계 쿼리의 결과다. */
public interface DailyAmount {

  LocalDate getDay();

  long getAmount();
}
