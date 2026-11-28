package com.back.nbe1214finalhot6.domain.cashback.repository;

/** 한 달의 월 적립 한도 사용률 구간별 이용자 수. 구간 집계 쿼리의 결과다. */
public interface LimitUsageCount {

  long getBelow25();

  long getBelow50();

  long getBelow75();

  long getBelow100();

  long getReached();
}
