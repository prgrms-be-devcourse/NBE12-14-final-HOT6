package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.util.List;

/**
 * 월 예산 하나의 일별 시계열. 운영자 대시보드의 예산 현황 그래프가 월마다 한 시리즈씩 그린다.
 *
 * @param year 월 예산의 연도
 * @param month 월 예산의 월
 * @param allocatedAmount 월 배정(월 배정선)
 * @param contingencyAmount 대비금(대비금선)
 * @param points 조회 기간 안의 날짜별 점. 날짜 순서이고, 변동이 없는 날도 직전 값을 이어서 담는다
 */
public record BudgetMonthSeries(
    int year,
    int month,
    long allocatedAmount,
    long contingencyAmount,
    List<BudgetTimePoint> points) {}
