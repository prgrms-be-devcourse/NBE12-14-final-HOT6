package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;

/** 적립 건을 상태별로 묶은 건수와 금액 합계. */
public interface EarningStatusTotal {

  EarningStatus getStatus();

  long getCount();

  long getAmount();
}
