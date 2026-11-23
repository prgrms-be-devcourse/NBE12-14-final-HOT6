package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetCloseRunResponse;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** 월 마감 스케줄러 연결. 테스트 프로필에서는 {@code cashback.scheduler.enabled=false} 라서 스케줄러가 등록되지 않는다. */
@IntegrationTest
class CashbackBudgetCloseSchedulerTest {

  @Autowired private ApplicationContext context;

  // 목적: 스케줄러 실행이 월 마감 배치를 한 번 호출하는지 확인한다(주기 실행 자체는 스프링이 한다).
  @Test
  @DisplayName("스케줄러 실행은 월 마감 배치를 한 번 호출한다")
  void run_callsClose() {
    CashbackBudgetCloseService closeService = mock(CashbackBudgetCloseService.class);
    given(closeService.run()).willReturn(new BudgetCloseRunResponse(0, 0, 0));

    new CashbackBudgetCloseScheduler(closeService).run();

    verify(closeService).run();
  }

  // 목적: 설정으로 스케줄러를 끄면 빈이 등록되지 않아 테스트 중에 저절로 돌지 않는지 확인한다.
  @Test
  @DisplayName("스케줄러를 끄면 빈이 등록되지 않는다")
  void disabled_notRegistered() {
    assertThat(context.getBeansOfType(CashbackBudgetCloseScheduler.class)).isEmpty();
  }
}
