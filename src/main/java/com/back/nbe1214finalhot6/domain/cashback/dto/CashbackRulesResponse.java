package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "운영자가 보는 캐시백 운영 규칙(서버 설정값)")
public record CashbackRulesResponse(
    @Schema(description = "대비금 비율. 월 배정 × 비율을 대비금으로 확정한다", example = "0.10") BigDecimal contingencyRate,
    @Schema(description = "긴급선 비율. 대비금 × 비율까지 남으면 즉시 중단한다", example = "0.5") BigDecimal emergencyRatio,
    @Schema(description = "공지 후 중단까지의 일수. 공지일 + N일 00:00(KST)에 중단한다", example = "3") int noticeDays,
    @Schema(description = "지급 지연 일수. 결제 다음 날 00:00 + N일에 지급한다", example = "3") int payoutDelayDays) {

  public static CashbackRulesResponse from(CashbackProperties properties) {
    return new CashbackRulesResponse(
        properties.contingencyRate(),
        properties.emergencyRatio(),
        properties.noticeDays(),
        properties.payoutDelayDays());
  }
}
