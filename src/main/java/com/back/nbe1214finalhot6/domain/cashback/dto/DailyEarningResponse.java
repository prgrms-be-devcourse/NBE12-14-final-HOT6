package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 하루의 신규 적립액.
 *
 * @param amount 그날 접수한 적립의 합(적립 예정과 지급 완료)
 * @param previousAverage 직전 7일의 하루 평균(원 미만 반올림). 그날은 넣지 않는다
 * @param rate 그날 끝(오늘은 지금)에 적용한 요율. 예산이 없는 날은 null
 */
public record DailyEarningResponse(
    LocalDate date, long amount, long previousAverage, BigDecimal rate) {}
