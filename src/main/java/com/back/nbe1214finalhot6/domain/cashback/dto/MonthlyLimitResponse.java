package com.back.nbe1214finalhot6.domain.cashback.dto;

/**
 * 이용자의 이번 달 월 적립 한도 현황. 금액은 원 단위다.
 *
 * @param year 한도 기준 연도(한국 시간 기준 이번 달)
 * @param month 한도 기준 월
 * @param limit 월 적립 한도(운영자 설정값)
 * @param usedAmount 이번 달에 쓴 한도. 적립 예정 건을 포함하고, 취소하면 줄어든다
 * @param remaining 남은 한도. 한도를 사용량보다 낮췄으면 0
 */
public record MonthlyLimitResponse(int year, int month, long limit, long usedAmount, long remaining) {}
