package com.back.nbe1214finalhot6.domain.cashback.dto;

import jakarta.validation.constraints.NotNull;

/** 월 예산 배정 요청. 금액은 원 단위다. */
public record MonthlyBudgetRequest(@NotNull Long allocatedAmount) {}
