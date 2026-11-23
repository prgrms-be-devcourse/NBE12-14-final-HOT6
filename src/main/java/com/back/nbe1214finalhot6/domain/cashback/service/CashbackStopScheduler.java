package com.back.nbe1214finalhot6.domain.cashback.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 공지 후 중단 예약 시각이 지난 월 예산을 중단한다. 점검 주기는 {@code cashback.scheduler.stop-check-interval-seconds}(기본 60초)다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "cashback.scheduler",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CashbackStopScheduler {

  private final CashbackBudgetStateService stateService;

  @Scheduled(
      fixedDelayString = "${cashback.scheduler.stop-check-interval-seconds:60}",
      timeUnit = TimeUnit.SECONDS)
  public void run() {
    int stopped = stateService.stopDueScheduled();
    if (stopped > 0) {
      log.info("캐시백 적립 중단(예약 시각 도달) {}건", stopped);
    }
  }
}
