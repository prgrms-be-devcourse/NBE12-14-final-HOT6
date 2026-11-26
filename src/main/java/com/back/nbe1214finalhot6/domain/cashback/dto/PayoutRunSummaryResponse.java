package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPayoutRun;
import com.back.nbe1214finalhot6.domain.cashback.entity.PayoutTrigger;
import java.time.LocalDateTime;

/** 지급 배치 실행 목록의 한 줄. */
public record PayoutRunSummaryResponse(
    Long runId, LocalDateTime ranAt, PayoutTrigger trigger, int paid, int skipped, int failed) {

  public static PayoutRunSummaryResponse from(CashbackPayoutRun run) {
    return new PayoutRunSummaryResponse(
        run.getId(),
        run.getRanAt(),
        run.getTriggerType(),
        run.getPaidCount(),
        run.getSkippedCount(),
        run.getFailedCount());
  }
}
