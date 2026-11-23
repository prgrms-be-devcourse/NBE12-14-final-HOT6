package com.back.nbe1214finalhot6.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.back.nbe1214finalhot6.domain.settlement.dto.MerchantSales;
import com.back.nbe1214finalhot6.domain.settlement.dto.SettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MerchantSalesRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class DailySettlementCatchUpTest {

  private static final LocalDate D15 = LocalDate.of(2026, 11, 15);
  private static final LocalDate D16 = LocalDate.of(2026, 11, 16);
  private static final LocalDate D17 = LocalDate.of(2026, 11, 17);

  private final MerchantSalesRepository salesRepository = mock(MerchantSalesRepository.class);
  private final DailySettlementRepository settlementRepository =
      mock(DailySettlementRepository.class);
  private final DailySettlementProcessor processor = mock(DailySettlementProcessor.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-11-18T00:00:00Z"), ZoneId.of("Asia/Seoul"));
  private final DailySettlementService service =
      new DailySettlementService(salesRepository, settlementRepository, processor, clock);

  @Test
  @DisplayName("한 날짜에서 가맹점 정산이 실패하면 이후 날짜는 정산하지 않고 멈춘다")
  void 실패하면_이후날짜_중단() {
    given(settlementRepository.findLatestSettlementDate()).willReturn(Optional.of(D15));
    given(salesRepository.sumByCompletedAt(eq(D16.atStartOfDay()), eq(D17.atStartOfDay()), any()))
        .willReturn(List.of(new MerchantSales(1L, 1_000L)));
    given(processor.settleOne(eq(1L), eq(D16), anyLong(), anyLong()))
        .willThrow(new IllegalStateException("저장 실패"));

    List<SettlementRunResult> results = service.settleUntil(D17);

    assertThat(results).extracting(SettlementRunResult::date).containsExactly(D15, D16);
    assertThat(results.get(1).failed()).isEqualTo(1);
    verify(processor, never()).settleOne(any(), eq(D17), anyLong(), anyLong());
  }

  @Test
  @DisplayName("동시 실행으로 이미 저장된 건의 유니크 위반은 건너뜀으로 센다")
  void 유니크위반_이미저장됨_건너뜀() {
    given(salesRepository.sumByCompletedAt(eq(D16.atStartOfDay()), eq(D17.atStartOfDay()), any()))
        .willReturn(List.of(new MerchantSales(1L, 1_000L)));
    given(processor.settleOne(eq(1L), eq(D16), anyLong(), anyLong()))
        .willThrow(new DataIntegrityViolationException("중복"));
    given(settlementRepository.existsByMerchantIdAndSettlementDate(1L, D16)).willReturn(true);

    SettlementRunResult result = service.settle(D16);

    assertThat(result.skipped()).isEqualTo(1);
    assertThat(result.failed()).isZero();
  }

  @Test
  @DisplayName("저장된 정산이 없는데 무결성 오류가 나면 건너뛰지 않고 실패로 센다")
  void 무결성오류_저장안됨_실패() {
    given(salesRepository.sumByCompletedAt(eq(D16.atStartOfDay()), eq(D17.atStartOfDay()), any()))
        .willReturn(List.of(new MerchantSales(1L, 1_000L)));
    given(processor.settleOne(eq(1L), eq(D16), anyLong(), anyLong()))
        .willThrow(new DataIntegrityViolationException("not null 위반"));
    given(settlementRepository.existsByMerchantIdAndSettlementDate(1L, D16)).willReturn(false);

    SettlementRunResult result = service.settle(D16);

    assertThat(result.skipped()).isZero();
    assertThat(result.failed()).isEqualTo(1);
  }
}
