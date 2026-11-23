package com.back.nbe1214finalhot6.domain.cashback.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 캐시백 설정값. 숫자는 코드에 박지 않고 여기서 조정한다.
 *
 * @param contingencyRate 대비금 비율. 월 배정 × 비율을 월 시작 시 확정한다
 * @param emergencyRatio 긴급선 비율. 대비금 × 비율까지 남으면 즉시 중단한다
 * @param noticeDays 공지 후 중단까지의 일수. 공지일 + N일 00:00(KST)에 중단한다
 * @param payoutDelayDays 지급 지연 일수. 결제 다음날 00:00 + N일에 지급한다
 * @param rateChangeNoticeDays 요율 인하 예고 일수. 적용 시각은 현재 분 + N일 이후여야 한다(0 이면 즉시 가능)
 * @param maxRateReductionsPerMonth 한 달에 요율을 인하할 수 있는 최대 횟수
 * @param scheduler 스케줄러 설정
 */
@ConfigurationProperties(prefix = "cashback")
public record CashbackProperties(
    @DefaultValue("0.10") BigDecimal contingencyRate,
    @DefaultValue("0.5") BigDecimal emergencyRatio,
    @DefaultValue("3") int noticeDays,
    @DefaultValue("3") int payoutDelayDays,
    @DefaultValue("0") int rateChangeNoticeDays,
    @DefaultValue("1") int maxRateReductionsPerMonth,
    @DefaultValue Scheduler scheduler) {

  /**
   * @param enabled false 면 스케줄러가 돌지 않는다(테스트에서는 배치 메서드를 직접 호출한다)
   * @param payoutIntervalSeconds 지급 배치 실행 간격(초). 데모에서는 10초로 줄인다
   * @param payoutBatchSize 지급 배치가 한 번에 읽는 건수
   * @param stopCheckIntervalSeconds 중단 예약 시각 도달 점검 간격(초)
   */
  public record Scheduler(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("3600") long payoutIntervalSeconds,
      @DefaultValue("200") int payoutBatchSize,
      @DefaultValue("60") long stopCheckIntervalSeconds) {}
}
