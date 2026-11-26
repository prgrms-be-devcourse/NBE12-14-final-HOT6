package com.back.nbe1214finalhot6.domain.cashback.dto;

/**
 * 지금의 캐시백 적립 상태. 상태 스트림의 {@code status} 이벤트에 담긴다.
 *
 * @param paused 이번 달 적립이 중단됐는지(월 예산이 중단·마감 상태). 공지 상태는 적립이 계속되므로 false
 */
public record CashbackStatusResponse(boolean paused) {}
