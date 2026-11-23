package com.back.nbe1214finalhot6.domain.cashback.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 한 달에 속한 적립 건의 가장 늦은 지급·취소 시각. 월 예산의 시계열이 어느 날짜까지 이어지는지 판단할 때 쓴다. */
public interface EarningExtent {

  LocalDate getLimitMonth();

  LocalDateTime getLastPaidAt();

  LocalDateTime getLastCanceledAt();
}
