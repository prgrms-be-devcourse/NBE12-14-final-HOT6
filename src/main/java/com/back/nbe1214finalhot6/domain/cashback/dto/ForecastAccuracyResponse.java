package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.util.List;

/**
 * 소진 예측의 오차. 예측을 저장한 지난 날만 비교한다.
 *
 * @param averageError 차이의 크기(부호 없음)의 평균(원). 비교한 날이 없으면 null
 * @param withinRangeDays 실제가 빠름~느림 범위 안이었던 날 수
 * @param days 최근 날짜부터
 */
public record ForecastAccuracyResponse(
    Long averageError, int withinRangeDays, List<ForecastAccuracyDay> days) {}
