package com.back.nbe1214finalhot6.domain.settlement.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.dto.SettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.service.DailySettlementService;
import com.back.nbe1214finalhot6.domain.settlement.service.MonthlySettlementService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MonthlySettlementSchedulerTest {

  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

  private final DailySettlementService dailyService = mock(DailySettlementService.class);
  private final MonthlySettlementService monthlyService = mock(MonthlySettlementService.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-11-18T00:00:00Z"), ZoneId.of("Asia/Seoul"));
  private final MonthlySettlementScheduler scheduler =
      new MonthlySettlementScheduler(dailyService, monthlyService, clock);

  @Test
  @DisplayName("일 정산이 모두 성공하면 전월 월 정산 건을 만들고 지급한다")
  void 일정산_성공시_전월_정산() {
    given(dailyService.settleUntil(LocalDate.of(2026, 11, 17)))
        .willReturn(List.of(new SettlementRunResult(LocalDate.of(2026, 11, 17), 1, 0, 0)));
    given(monthlyService.settle(OCTOBER))
        .willReturn(new MonthlySettlementRunResult(OCTOBER, 1, 1, 0));

    scheduler.settleLastMonth();

    verify(monthlyService).settle(OCTOBER);
    verify(monthlyService, never()).payPending(any());
  }

  @Test
  @DisplayName("전월 말일 이전의 일 정산이 실패하면 건은 만들지 않고 대기 건만 지급한다")
  void 전월_일정산_실패시_대기건만_지급() {
    given(dailyService.settleUntil(LocalDate.of(2026, 11, 17)))
        .willReturn(List.of(new SettlementRunResult(LocalDate.of(2026, 10, 31), 0, 0, 1)));
    given(monthlyService.payPending(OCTOBER))
        .willReturn(new MonthlySettlementRunResult(OCTOBER, 0, 0, 0));

    scheduler.settleLastMonth();

    verify(monthlyService).payPending(OCTOBER);
    verify(monthlyService, never()).settle(any());
  }

  @Test
  @DisplayName("전월 말일 이후의 일 정산이 실패해도 전월 월 정산은 진행한다")
  void 전월_이후_일정산_실패는_영향없음() {
    given(dailyService.settleUntil(LocalDate.of(2026, 11, 17)))
        .willReturn(
            List.of(
                new SettlementRunResult(LocalDate.of(2026, 11, 1), 1, 0, 0),
                new SettlementRunResult(LocalDate.of(2026, 11, 2), 0, 0, 1)));
    given(monthlyService.settle(OCTOBER))
        .willReturn(new MonthlySettlementRunResult(OCTOBER, 1, 1, 0));

    scheduler.settleLastMonth();

    verify(monthlyService).settle(OCTOBER);
    verify(monthlyService, never()).payPending(any());
  }
}
