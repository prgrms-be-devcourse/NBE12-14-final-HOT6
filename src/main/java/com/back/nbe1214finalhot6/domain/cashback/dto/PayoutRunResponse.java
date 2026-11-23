package com.back.nbe1214finalhot6.domain.cashback.dto;

/**
 * 지급 배치 한 번의 결과.
 *
 * @param paid 지갑에 입금하고 지급 완료로 바꾼 건수
 * @param skipped 지급하지 않고 넘어간 건수. 고르는 사이에 취소됐거나 다른 배치가 먼저 지급한 건이다
 * @param failed 처리 중 오류가 나서 적립 예정으로 남긴 건수. 다음 배치에서 다시 시도한다
 */
public record PayoutRunResponse(int paid, int skipped, int failed) {}
