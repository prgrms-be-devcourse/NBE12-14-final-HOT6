package com.back.nbe1214finalhot6.domain.cashback.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 월 예산 이월 요청. 해당 월의 남은 예산에서 금액을 다음 달로 넘긴다.
 *
 * @param year 이월을 내보내는 달의 연도
 * @param month 이월을 내보내는 달(1~12). 다음 달로만 넘긴다
 * @param amount 이월할 금액(원). 0보다 크고 그 달의 남은 예산 이하
 */
public record BudgetTransferRequest(
    @NotNull Integer year, @NotNull Integer month, @NotNull Long amount) {}
