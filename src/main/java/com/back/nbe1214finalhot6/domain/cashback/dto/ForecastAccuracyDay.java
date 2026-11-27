package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 하루의 예측과 실제.
 *
 * @param predictedDemand 그날 저장한 하루 예상 수요(중간)
 * @param actualAmount 그날 접수해 확보한 적립액(취소 제외)
 * @param difference 실제 - 예상(원). 양수면 예상보다 많이 썼다
 * @param withinRange 실제가 느림 이상 빠름 이하였는지
 */
public record ForecastAccuracyDay(
    LocalDate date,
    BigDecimal predictedDemand,
    BigDecimal slowDemand,
    BigDecimal fastDemand,
    long actualAmount,
    long difference,
    boolean withinRange,
    String modelVersion) {}
