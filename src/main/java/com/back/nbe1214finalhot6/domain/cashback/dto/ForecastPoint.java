package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.time.LocalDate;

/**
 * 예상 선의 하루치 점. 그날 끝의 남은 예산(원)을 시나리오별로 담고, 소진된 뒤에는 0이다.
 *
 * @param date 날짜. 기준일 다음 날부터 월말까지
 */
public record ForecastPoint(
    LocalDate date, long fastRemaining, long midRemaining, long slowRemaining) {}
