package com.back.nbe1214finalhot6.domain.cashback.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 요율을 인하한 시점의 근거. 서버가 저장할 때의 값을 그대로 남기고 이후 바꾸지 않는다.
 *
 * <p>예산과 예측은 저장한 날이 속한 달의 값이다. 그 달의 월 예산이 없으면 직전 요율만 남고 나머지는 null 이다.
 *
 * @param previousRate 인하 전에 적용 시각에 적용되던 요율
 * @param dailyDemand 최근 7일의 하루 평균 신규 적립 확보액(원)
 * @param fastExhaustionDate 수요가 빠를 때의 예상 소진일. 이번 달 안에 소진되지 않으면 null
 * @param noticeLineDate 중앙값 기준 대비금 도달일. 이번 달 안에 닿지 않으면 null
 * @param modelVersion 예측 모델 버전
 */
public record PolicyBasis(
    BigDecimal previousRate,
    Integer year,
    Integer month,
    Long allocatedAmount,
    Long paidAmount,
    Long reservedAmount,
    Long remainingAmount,
    Long contingencyAmount,
    BigDecimal dailyDemand,
    LocalDate fastExhaustionDate,
    LocalDate midExhaustionDate,
    LocalDate slowExhaustionDate,
    LocalDate noticeLineDate,
    String modelVersion) {

  /** 이번 달 예산이 없어 직전 요율만 남기는 경우. */
  public static PolicyBasis rateOnly(BigDecimal previousRate) {
    return new PolicyBasis(
        previousRate, null, null, null, null, null, null, null, null, null, null, null, null, null);
  }

  /** 직전 요율을 채운 근거. 예산과 예측은 요율을 정하기 전에 모은다. */
  public PolicyBasis withPreviousRate(BigDecimal rate) {
    return new PolicyBasis(
        rate,
        year,
        month,
        allocatedAmount,
        paidAmount,
        reservedAmount,
        remainingAmount,
        contingencyAmount,
        dailyDemand,
        fastExhaustionDate,
        midExhaustionDate,
        slowExhaustionDate,
        noticeLineDate,
        modelVersion);
  }
}
