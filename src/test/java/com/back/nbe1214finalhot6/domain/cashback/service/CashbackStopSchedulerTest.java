package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** 중단 스케줄러 연결. 테스트 프로필에서는 {@code cashback.scheduler.enabled=false} 라서 스케줄러가 등록되지 않는다. */
@IntegrationTest
class CashbackStopSchedulerTest {

  @Autowired private ApplicationContext context;

  // 목적: 스케줄러 실행이 상태 서비스의 예약 시각 처리를 한 번 호출하는지 확인한다(주기 실행 자체는 스프링이 한다).
  @Test
  @DisplayName("스케줄러 실행은 예약 시각 처리를 한 번 호출한다")
  void run_callsStopDueScheduled() {
    CashbackBudgetStateService stateService = mock(CashbackBudgetStateService.class);
    given(stateService.stopDueScheduled()).willReturn(0);

    new CashbackStopScheduler(stateService).run();

    verify(stateService).stopDueScheduled();
  }

  // 목적: 설정으로 스케줄러를 끄면 빈이 등록되지 않아 테스트 중에 저절로 돌지 않는지 확인한다.
  @Test
  @DisplayName("스케줄러를 끄면 빈이 등록되지 않는다")
  void disabled_notRegistered() {
    assertThat(context.getBeansOfType(CashbackStopScheduler.class)).isEmpty();
  }
}
