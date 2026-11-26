package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.PayoutTrigger;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** 스케줄러 연결. 테스트 프로필에서는 {@code cashback.scheduler.enabled=false} 라서 스케줄러가 등록되지 않는다. */
@IntegrationTest
class CashbackPayoutSchedulerTest {

  @Autowired private ApplicationContext context;

  // 목적: 스케줄러가 배치 서비스를 한 번 호출하는지 확인한다(주기 실행 자체는 스프링이 한다).
  @Test
  @DisplayName("스케줄러 실행은 지급 배치를 자동 실행으로 한 번 호출한다")
  void run_callsPayout() {
    CashbackPayoutService payoutService = mock(CashbackPayoutService.class);
    given(payoutService.runAndRecord(PayoutTrigger.SCHEDULED))
        .willReturn(new PayoutRunResponse(0, 0, 0));

    new CashbackPayoutScheduler(payoutService).run();

    verify(payoutService).runAndRecord(PayoutTrigger.SCHEDULED);
  }

  // 목적: 설정으로 스케줄러를 끄면 빈이 등록되지 않아 테스트 중 배치가 저절로 돌지 않는지 확인한다.
  @Test
  @DisplayName("스케줄러를 끄면 빈이 등록되지 않는다")
  void disabled_notRegistered() {
    assertThat(context.getBeansOfType(CashbackPayoutScheduler.class)).isEmpty();
  }
}
