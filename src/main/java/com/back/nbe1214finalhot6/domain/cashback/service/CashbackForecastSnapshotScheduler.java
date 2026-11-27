package com.back.nbe1214finalhot6.domain.cashback.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 그날의 소진 예측을 한 번 저장한다. 주기({@code cashback.scheduler.forecast-snapshot-interval-seconds}, 기본 600초)마다
 * 확인하므로 날짜가 바뀌거나 서버를 켠 뒤 곧 저장된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "cashback.scheduler",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CashbackForecastSnapshotScheduler {

  private final CashbackForecastAccuracyService accuracyService;

  @Scheduled(
      fixedDelayString = "${cashback.scheduler.forecast-snapshot-interval-seconds:600}",
      timeUnit = TimeUnit.SECONDS)
  public void run() {
    if (accuracyService.recordToday()) {
      log.info("캐시백 소진 예측 저장");
    }
  }
}
