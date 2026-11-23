package com.back.nbe1214finalhot6.domain.reconciliation.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReconciliationResultTest {

  private static final LocalDate DATE = LocalDate.of(2026, 11, 17);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 0, 50);

  @Test
  @DisplayName("불일치가 없으면 일치 상태로 기록한다")
  void 불일치없음_일치() {
    ReconciliationResult result =
        ReconciliationResult.of(ReconciliationType.DAILY_NET_SALES, DATE, 3, List.of(), NOW);

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
    assertThat(result.getCheckedCount()).isEqualTo(3);
    assertThat(result.getMismatchCount()).isZero();
    assertThat(result.getMismatchRefs()).isNull();
  }

  @Test
  @DisplayName("불일치가 있으면 건수와 대상을 기록한다")
  void 불일치있음_대상기록() {
    ReconciliationResult result =
        ReconciliationResult.of(
            ReconciliationType.SETTLEMENT_CARRY,
            DATE,
            5,
            List.of("merchantId=1", "merchantId=4"),
            NOW);

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
    assertThat(result.getMismatchCount()).isEqualTo(2);
    assertThat(result.getMismatchRefs()).isEqualTo("merchantId=1,merchantId=4");
  }

  @Test
  @DisplayName("검사 자체가 실패하면 오류 상태와 메시지를 기록한다")
  void 검사실패_오류상태() {
    ReconciliationResult result =
        ReconciliationResult.error(ReconciliationType.MONTHLY_PAYOUT, DATE, "연결 실패", NOW);

    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.ERROR);
    assertThat(result.getErrorMessage()).isEqualTo("연결 실패");
    assertThat(result.getMismatchCount()).isZero();
  }

  @Test
  @DisplayName("불일치 대상이 너무 길면 잘라서 저장하되 건수는 전체를 센다")
  void 대상목록_길이제한() {
    List<String> refs = IntStream.range(0, 500).mapToObj(i -> "merchantId=" + i).toList();

    ReconciliationResult result =
        ReconciliationResult.of(ReconciliationType.DAILY_NET_SALES, DATE, 500, refs, NOW);

    assertThat(result.getMismatchCount()).isEqualTo(500);
    assertThat(result.getMismatchRefs()).hasSizeLessThanOrEqualTo(2000);
  }
}
