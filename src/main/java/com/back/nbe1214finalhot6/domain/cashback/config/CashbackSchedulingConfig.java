package com.back.nbe1214finalhot6.domain.cashback.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 캐시백 스케줄러를 켠다. {@code cashback.scheduler.enabled=false} 면 켜지 않는다(테스트에서는 배치 메서드를 직접 호출한다). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "cashback.scheduler",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CashbackSchedulingConfig {}
