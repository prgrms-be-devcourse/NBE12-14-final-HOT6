package com.back.nbe1214finalhot6.domain.cashback.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 월 예산 배정 요청. 금액은 원 단위다.
 *
 * @param baseRate 그 달의 기본 요율(소수 비율, 3% = 0.03). 생략하면 기존 값을 유지하고, 새로 만드는 달이면 가장 가까운 이전 달의 값을 쓴다
 */
public record MonthlyBudgetRequest(@NotNull Long allocatedAmount, BigDecimal baseRate) {}
