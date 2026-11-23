package com.back.nbe1214finalhot6.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 탈퇴 실행 결과. */
@Schema(description = "탈퇴 실행 결과")
public record WithdrawalResult(
    @Schema(description = "모의계좌로 환불한 데브머니", example = "50000") long refundedAmount,
    @Schema(description = "소멸시킨 적립 예정(PENDING) 캐시백 건수", example = "2") int canceledEarningCount,
    @Schema(description = "소멸시킨 적립 예정 캐시백 합계", example = "1500") long canceledEarningAmount,
    @Schema(description = "탈퇴 시각", example = "2026-11-24T10:00:00") LocalDateTime withdrawnAt) {}
