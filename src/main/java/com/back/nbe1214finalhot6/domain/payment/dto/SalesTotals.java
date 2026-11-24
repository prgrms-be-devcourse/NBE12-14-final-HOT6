package com.back.nbe1214finalhot6.domain.payment.dto;

/** 기간 안 결제(또는 취소)의 건수와 금액 합계. 사장님 결제 내역의 집계 쿼리 결과다. */
public record SalesTotals(Long count, Long amount) {}
