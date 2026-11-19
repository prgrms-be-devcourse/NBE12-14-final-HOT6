package com.back.nbe1214finalhot6.domain.cashback.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 연 예산 등록·수정 요청.
 *
 * @param totalAmount 연 예산 총액(원). 0보다 커야 한다
 */
public record AnnualBudgetRequest(@NotNull Long totalAmount) {}
