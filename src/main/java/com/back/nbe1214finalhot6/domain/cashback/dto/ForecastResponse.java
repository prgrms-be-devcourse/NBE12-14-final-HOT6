package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.util.List;

/**
 * 월 예산의 소진 예측. 참고 정보이며 판단의 필수 입력이 아니다.
 *
 * @param remainingAmount 기준일의 남은 예산(원)
 * @param contingencyAmount 대비금(원). 남은 예산이 이 값에 닿으면 공지 상태가 된다
 * @param guaranteedUsers 최소 보장 인원. 남은 예산으로 이용자 월 적립 한도까지 받을 수 있는 이용자 수(내림)
 * @param points 예상 선. 기준일 다음 날부터 월말까지 시나리오별 남은 예산. 실제 선은 시계열 API 가 담당한다
 */
public record ForecastResponse(
    int year,
    int month,
    long remainingAmount,
    long contingencyAmount,
    long guaranteedUsers,
    ForecastAssumptions assumptions,
    ForecastScenario fast,
    ForecastScenario mid,
    ForecastScenario slow,
    List<ForecastPoint> points) {}
