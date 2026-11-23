package com.back.nbe1214finalhot6.domain.cashback.dto;

/**
 * 월 마감 배치 한 번의 결과.
 *
 * @param closed 마감한 월 예산 수(남은 예산이 있으면 다음 달로 이월했다)
 * @param skipped 마감하지 않고 넘어간 월 예산 수. 다음 달 월 예산이 없어서 보류했거나 다른 곳에서 먼저 마감했다
 * @param failed 처리 중 오류가 나서 그대로 둔 월 예산 수. 다음 배치에서 다시 시도한다
 */
public record BudgetCloseRunResponse(int closed, int skipped, int failed) {}
