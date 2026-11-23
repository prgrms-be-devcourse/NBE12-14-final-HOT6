package com.back.nbe1214finalhot6.domain.settlement.scheduler;

import com.back.nbe1214finalhot6.domain.settlement.dto.SettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.service.DailySettlementService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailySettlementScheduler {

  private final DailySettlementService settlementService;
  private final Clock clock;

  @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
  public void settleYesterday() {
    LocalDate yesterday = LocalDate.now(clock).minusDays(1);
    for (SettlementRunResult result : settlementService.settleUntil(yesterday)) {
      if (result.failed() > 0) {
        log.warn(
            "일 정산 일부 실패 date={}, created={}, skipped={}, failed={}",
            result.date(),
            result.created(),
            result.skipped(),
            result.failed());
      } else {
        log.info(
            "일 정산 완료 date={}, created={}, skipped={}",
            result.date(),
            result.created(),
            result.skipped());
      }
    }
  }
}
