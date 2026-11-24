package com.back.nbe1214finalhot6.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 탈퇴 미리보기 응답. 실행 전에 환불 금액과 소멸될 적립 예정 캐시백을 보여 주고 확인받는 용도이며, 아무것도 바꾸지 않는다. */
@Schema(description = "탈퇴 미리보기 응답")
public record WithdrawalPreviewResponse(
    @Schema(description = "환불 예정액(남은 데브머니 전부). 모의계좌로 돌아간다", example = "50000") long refundAmount,
    @Schema(description = "소멸될 적립 예정(PENDING) 캐시백 건수", example = "2") int pendingCashbackCount,
    @Schema(description = "소멸될 적립 예정 캐시백 합계", example = "1500") long pendingCashbackAmount,
    @Schema(
            description = "현재 캐시백 잔액. 음수여도 탈퇴할 수 있고, 정산하지 않고 감수한다",
            example = "-200")
        long cashbackBalance) {}
