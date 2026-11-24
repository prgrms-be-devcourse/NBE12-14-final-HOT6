package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 예측에 쓴 가정과 버전. 같은 가정과 같은 데이터면 같은 예측이 나온다.
 *
 * @param demandBasis 수요 기준(예: {@code RECENT_7D_AVG} = 기준일을 뺀 최근 7일의 일평균 신규 적립 확보액)
 * @param baseDailyDemand 수요 배율을 곱하기 전 일평균(원). 소수 둘째 자리까지
 * @param demandMultiplier 수요 배율(설정값)
 * @param rangeRatio 빠름·느림 범위 폭(설정값). 빠름 = 중앙 × (1 + 폭), 느림 = 중앙 × (1 − 폭)
 * @param modelVersion 모델 버전
 * @param asOf 기준일(오늘)
 */
public record ForecastAssumptions(
    String demandBasis,
    BigDecimal baseDailyDemand,
    BigDecimal demandMultiplier,
    BigDecimal rangeRatio,
    String modelVersion,
    LocalDate asOf) {}
