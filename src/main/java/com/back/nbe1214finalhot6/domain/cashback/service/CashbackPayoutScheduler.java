package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.PayoutTrigger;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 지급 배치를 주기적으로 실행한다. 주기는 {@code cashback.scheduler.payout-interval-seconds}(기본 1시간, 데모에서는 10초)다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "cashback.scheduler",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CashbackPayoutScheduler {

  private final CashbackPayoutService payoutService;

  @Scheduled(
      fixedDelayString = "${cashback.scheduler.payout-interval-seconds:3600}",
      timeUnit = TimeUnit.SECONDS)
  public void run() {
    PayoutRunResponse result = payoutService.runAndRecord(PayoutTrigger.SCHEDULED);
    if (result.paid() + result.failed() > 0) {
      log.info(
          "캐시백 지급 배치 paid={} skipped={} failed={}",
          result.paid(),
          result.skipped(),
          result.failed());
    }
  }
}
