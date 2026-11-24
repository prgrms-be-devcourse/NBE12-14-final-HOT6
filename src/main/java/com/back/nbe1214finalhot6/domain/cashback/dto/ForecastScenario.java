package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 소진 예측의 한 시나리오(빠름·중앙·느림).
 *
 * @param dailyDemand 하루 적립 확보액(원). 소수 둘째 자리까지
 * @param exhaustionDate 예상 소진일. 이번 달 안에 소진되지 않거나 수요가 0이면 null. 이미 소진됐으면 기준일
 * @param noticeLineDate 대비금(공지선) 도달일. 이번 달 안에 닿지 않거나 수요가 0이면 null. 이미 닿았으면 기준일
 */
public record ForecastScenario(
    BigDecimal dailyDemand, LocalDate exhaustionDate, LocalDate noticeLineDate) {}
