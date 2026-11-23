package com.back.nbe1214finalhot6.domain.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationResult;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationStatus;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.exception.ReconciliationErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReconciliationServiceTest {

  private static final LocalDate DATE = LocalDate.of(2026, 11, 17);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 0, 50);

  private final ReconciliationCheck salesCheck = mock(ReconciliationCheck.class);
  private final ReconciliationCheck carryCheck = mock(ReconciliationCheck.class);
  private final ReconciliationRecorder recorder = mock(ReconciliationRecorder.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-11-18T00:00:00Z"), ZoneId.of("Asia/Seoul"));
  private final ReconciliationService service =
      new ReconciliationService(List.of(salesCheck, carryCheck), recorder, clock);

  @Test
  @DisplayName("한 종류의 검사가 실패해도 오류로 기록하고 나머지 종류는 계속 실행한다")
  void 한종류_실패해도_나머지_저장() {
    ReconciliationOutcome outcome = new ReconciliationOutcome(2, List.of());
    given(salesCheck.type()).willReturn(ReconciliationType.DAILY_NET_SALES);
    given(carryCheck.type()).willReturn(ReconciliationType.SETTLEMENT_CARRY);
    given(salesCheck.check(DATE)).willThrow(new IllegalStateException("조회 실패"));
    given(carryCheck.check(DATE)).willReturn(outcome);
    given(recorder.recordError(eq(ReconciliationType.DAILY_NET_SALES), eq(DATE), any()))
        .willReturn(
            ReconciliationResult.error(ReconciliationType.DAILY_NET_SALES, DATE, "조회 실패", NOW));
    given(recorder.record(ReconciliationType.SETTLEMENT_CARRY, DATE, outcome))
        .willReturn(
            ReconciliationResult.of(ReconciliationType.SETTLEMENT_CARRY, DATE, 2, List.of(), NOW));

    List<ReconciliationResult> results = service.run(DATE);

    assertThat(results).extracting(ReconciliationResult::getStatus)
        .containsExactly(ReconciliationStatus.ERROR, ReconciliationStatus.MATCHED);
    verify(recorder).record(ReconciliationType.SETTLEMENT_CARRY, DATE, outcome);
  }

  @Test
  @DisplayName("결과 저장이 실패해도 다음 종류의 검사는 실행한다")
  void 저장실패해도_다음종류_실행() {
    ReconciliationOutcome outcome = new ReconciliationOutcome(1, List.of());
    given(salesCheck.type()).willReturn(ReconciliationType.DAILY_NET_SALES);
    given(carryCheck.type()).willReturn(ReconciliationType.SETTLEMENT_CARRY);
    given(salesCheck.check(DATE)).willReturn(outcome);
    given(carryCheck.check(DATE)).willReturn(outcome);
    given(recorder.record(ReconciliationType.DAILY_NET_SALES, DATE, outcome))
        .willThrow(new IllegalStateException("저장 실패"));
    given(recorder.recordError(eq(ReconciliationType.DAILY_NET_SALES), eq(DATE), any()))
        .willThrow(new IllegalStateException("저장 실패"));
    given(recorder.record(ReconciliationType.SETTLEMENT_CARRY, DATE, outcome))
        .willReturn(
            ReconciliationResult.of(ReconciliationType.SETTLEMENT_CARRY, DATE, 1, List.of(), NOW));

    List<ReconciliationResult> results = service.run(DATE);

    assertThat(results).hasSize(1);
    verify(carryCheck).check(DATE);
  }

  @Test
  @DisplayName("오늘 이후 날짜는 대사할 수 없다")
  void 오늘이후_거절() {
    assertThatThrownBy(() -> service.run(LocalDate.of(2026, 11, 18)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(ReconciliationErrorCode.RECONCILIATION_DATE_NOT_ALLOWED));
    verify(salesCheck, never()).check(any());
  }
}
