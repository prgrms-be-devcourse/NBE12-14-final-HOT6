package com.back.nbe1214finalhot6.domain.cashback.repository;

/** 지급 대기 적립 건의 건수와 금액 합계. */
public interface PayableTotal {

  long getCount();

  long getAmount();
}
