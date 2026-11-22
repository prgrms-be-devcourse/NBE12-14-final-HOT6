package com.back.nbe1214finalhot6.domain.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 지갑 조회 응답")
public record WalletResponse(
    @Schema(description = "데브머니(충전금) 잔액", example = "150000") long devMoney,
    @Schema(description = "캐시백 잔액. 지급된 캐시백 회수로 음수가 될 수 있음", example = "-200")
        long cashbackBalance,
    @Schema(description = "결제에서 쓸 수 있는 캐시백. 캐시백 잔액이 음수면 0", example = "0")
        long availableCashback,
    @Schema(description = "보유 한도(운영자 설정)", example = "2000000") long holdingLimit,
    @Schema(description = "월 충전 한도(운영자 설정)", example = "1000000") long monthlyChargeLimit,
    @Schema(description = "이번 달(한국 시간 달력월) 누적 충전액. 환불해도 줄지 않음", example = "600000")
        long chargedThisMonth,
    @Schema(description = "남은 충전 가능액. 보유 한도와 월 충전 한도 중 더 빡빡한 쪽, 한도를 넘었으면 0", example = "400000")
        long chargeableAmount) {}
