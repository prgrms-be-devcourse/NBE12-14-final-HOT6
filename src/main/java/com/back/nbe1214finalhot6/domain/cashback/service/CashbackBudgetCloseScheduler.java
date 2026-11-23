package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetCloseRunResponse;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 월 마감 배치를 주기적으로 실행한다. 점검 주기는 {@code cashback.scheduler.close-check-interval-seconds}(기본 1시간)다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "cashback.scheduler",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CashbackBudgetCloseScheduler {

  private final CashbackBudgetCloseService closeService;

  @Scheduled(
      fixedDelayString = "${cashback.scheduler.close-check-interval-seconds:3600}",
      timeUnit = TimeUnit.SECONDS)
  public void run() {
    BudgetCloseRunResponse result = closeService.run();
    if (result.closed() + result.failed() > 0) {
      log.info(
          "캐시백 월 마감 closed={} skipped={} failed={}",
          result.closed(),
          result.skipped(),
          result.failed());
    }
  }
}
