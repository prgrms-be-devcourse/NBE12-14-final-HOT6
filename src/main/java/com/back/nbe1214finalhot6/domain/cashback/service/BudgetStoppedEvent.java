package com.back.nbe1214finalhot6.domain.cashback.service;

/** 월 예산이 중단 상태로 바뀌었다. 전이한 트랜잭션 안에서 발행하며, 받는 쪽은 커밋된 뒤에 처리한다. */
public record BudgetStoppedEvent(Long monthlyBudgetId) {}
