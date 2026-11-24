package com.back.nbe1214finalhot6.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.dto.SettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 기준 시각은 2026-11-18 09:00 KST 이므로 지난달은 10월이다.
class SettlementRunServiceTest {

  private static final LocalDate D15 = LocalDate.of(2026, 11, 15);
  private static final LocalDate D16 = LocalDate.of(2026, 11, 16);
  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
  private static final LocalDate OCTOBER_END = LocalDate.of(2026, 10, 31);

  private final DailySettlementService dailyService = mock(DailySettlementService.class);
  private final MonthlySettlementService monthlyService = mock(MonthlySettlementService.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-11-18T00:00:00Z"), ZoneId.of("Asia/Seoul"));
  private final SettlementRunService service =
      new SettlementRunService(dailyService, monthlyService, clock);

  @Test
  @DisplayName("수동 일 정산은 이어서 정산한 날들의 결과를 합치고 마지막 날짜를 돌려준다")
  void 일정산_결과합산() {
    given(dailyService.settleUntil(D16))
        .willReturn(
            List.of(new SettlementRunResult(D15, 1, 2, 0), new SettlementRunResult(D16, 3, 0, 0)));

    SettlementRunResult result = service.runDaily(D16);

    assertThat(result.date()).isEqualTo(D16);
    assertThat(result.created()).isEqualTo(4);
    assertThat(result.skipped()).isEqualTo(2);
    assertThat(result.failed()).isZero();
  }

  @Test
  @DisplayName("수동 일 정산에서 실패한 가맹점이 있으면 실패 건수를 합쳐 돌려주고 멈춘 날짜를 알려 준다")
  void 일정산_실패합산() {
    given(dailyService.settleUntil(D16))
        .willReturn(
            List.of(new SettlementRunResult(D15, 1, 0, 0), new SettlementRunResult(D16, 0, 0, 2)));

    SettlementRunResult result = service.runDaily(D16);

    assertThat(result.date()).isEqualTo(D16);
    assertThat(result.failed()).isEqualTo(2);
  }

  @Test
  @DisplayName("수동 월 정산은 일 정산을 먼저 채우고 실패가 없으면 월 정산을 실행한다")
  void 월정산_일정산완료_실행() {
    MonthlySettlementRunResult expected = new MonthlySettlementRunResult(OCTOBER, 2, 2, 0);
    given(dailyService.settleUntil(OCTOBER_END))
        .willReturn(List.of(new SettlementRunResult(OCTOBER_END, 1, 0, 0)));
    given(monthlyService.settle(OCTOBER)).willReturn(expected);

    assertThat(service.runMonthly(OCTOBER)).isEqualTo(expected);
  }

  @Test
  @DisplayName("수동 월 정산은 일 정산에 실패한 날이 있으면 월 정산 건을 만들지 않고 STL-005 로 막는다")
  void 월정산_일정산미완료_거절() {
    given(dailyService.settleUntil(OCTOBER_END))
        .willReturn(List.of(new SettlementRunResult(LocalDate.of(2026, 10, 30), 0, 0, 1)));

    assertThatThrownBy(() -> service.runMonthly(OCTOBER))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(SettlementErrorCode.DAILY_SETTLEMENT_INCOMPLETE));
    verify(monthlyService, never()).settle(any());
  }

  @Test
  @DisplayName("이번 달 이후는 일 정산도 채우지 않고 STL-003 으로 거절한다")
  void 월정산_이번달이후_거절() {
    assertThatThrownBy(() -> service.runMonthly(YearMonth.of(2026, 11)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_MONTH_NOT_ALLOWED));
    verify(dailyService, never()).settleUntil(any());
    verify(monthlyService, never()).settle(any());
  }
}
