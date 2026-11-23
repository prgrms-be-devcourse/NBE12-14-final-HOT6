package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackSummaryResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.EarningResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyLimitResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.service.OperatorSettingAdminService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 이용자 캐시백 조회. 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class CashbackQueryServiceTest {

  private static final long WALLET = 7L;
  private static final long OTHER = 8L;
  private static final YearMonth NOV = YearMonth.of(2026, 11);
  private static final YearMonth OCT = YearMonth.of(2026, 10);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 9, 0);

  @Autowired private CashbackQueryService queryService;
  @Autowired private CashbackMonthlyLimitService limitService;
  @Autowired private CashbackEarningRepository earningRepository;
  @Autowired private OperatorSettingAdminService settingAdminService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @PersistenceContext private EntityManager entityManager;

  private CashbackEarning pending(long paymentId, long walletId, LocalDateTime requestedAt) {
    return earningRepository.save(
        CashbackEarning.pending(
            paymentId,
            walletId,
            10_000L,
            requestedAt,
            new BigDecimal("0.03"),
            null,
            300L,
            requestedAt.toLocalDate().plusDays(4).atStartOfDay(),
            LocalDate.of(2026, 11, 1),
            NOW));
  }

  private CashbackEarning excluded(long paymentId, long walletId, LocalDateTime requestedAt) {
    return earningRepository.save(
        CashbackEarning.excluded(
            paymentId,
            walletId,
            10_000L,
            requestedAt,
            CashbackExclusionReason.MONTHLY_LIMIT_EXCEEDED,
            NOW));
  }

  private void setStatus(long paymentId, String status) {
    entityManager.flush();
    jdbcTemplate.update(
        "update cashback_earning set status = ? where payment_id = ?", status, paymentId);
    entityManager.clear();
  }

  // 목적: 사용 기록이 없는 이용자는 한도 전체가 남고 기준 월이 이번 달(시계 기준)로 나오는지 확인한다.
  @Test
  @DisplayName("사용 기록이 없으면 사용량 0 이고 한도 전체가 남는다")
  void monthlyLimit_noUsage() {
    MonthlyLimitResponse result = queryService.getMonthlyLimit(WALLET);

    assertThat(result).isEqualTo(new MonthlyLimitResponse(2026, 11, 50_000L, 0L, 50_000L));
  }

  // 목적: 이번 달 사용량만 합산되고(지난달·다른 이용자 제외), 취소로 복원하면 남은 한도가 다시 늘어나는지 확인한다.
  @Test
  @DisplayName("이번 달 사용량만 반영하고 복원하면 남은 한도가 늘어난다")
  void monthlyLimit_countsThisMonthOnly() {
    limitService.consume(WALLET, NOV, 12_000L, 50_000L);
    limitService.consume(WALLET, OCT, 9_000L, 50_000L);
    limitService.consume(OTHER, NOV, 20_000L, 50_000L);

    assertThat(queryService.getMonthlyLimit(WALLET).usedAmount()).isEqualTo(12_000L);
    assertThat(queryService.getMonthlyLimit(WALLET).remaining()).isEqualTo(38_000L);

    limitService.release(WALLET, NOV, 2_000L);

    assertThat(queryService.getMonthlyLimit(WALLET).remaining()).isEqualTo(40_000L);
  }

  // 목적: 운영자가 한도를 바꾸면 조회에 바로 반영되고, 사용량보다 낮추면 남은 한도가 음수가 아니라 0 인지 확인한다.
  @Test
  @DisplayName("한도를 바꾸면 바로 반영되고 사용량보다 낮추면 남은 한도는 0 이다")
  void monthlyLimit_followsOperatorSetting() {
    limitService.consume(WALLET, NOV, 12_000L, 50_000L);

    settingAdminService.put(SettingKey.CASHBACK_USER_MONTHLY_LIMIT.name(), 100_000L);
    assertThat(queryService.getMonthlyLimit(WALLET).limit()).isEqualTo(100_000L);
    assertThat(queryService.getMonthlyLimit(WALLET).remaining()).isEqualTo(88_000L);

    settingAdminService.put(SettingKey.CASHBACK_USER_MONTHLY_LIMIT.name(), 5_000L);
    MonthlyLimitResponse lowered = queryService.getMonthlyLimit(WALLET);
    assertThat(lowered.usedAmount()).isEqualTo(12_000L);
    assertThat(lowered.remaining()).isZero();
  }

  // 목적: 내 적립 건만 최근 접수순으로 나오고(다른 이용자 제외), 제외 건은 적립액 0 과 사유를 함께 보여 주는지 확인한다.
  @Test
  @DisplayName("내 적립 내역만 최근 접수순으로 보여 주고 제외 건은 사유를 담는다")
  void listEarnings_mineNewestFirst() {
    pending(1L, WALLET, LocalDateTime.of(2026, 11, 10, 9, 0));
    excluded(2L, WALLET, LocalDateTime.of(2026, 11, 17, 9, 0));
    pending(3L, WALLET, LocalDateTime.of(2026, 11, 12, 9, 0));
    pending(4L, OTHER, LocalDateTime.of(2026, 11, 18, 8, 0));
    entityManager.flush();
    entityManager.clear();

    PageResponse<EarningResponse> result = queryService.listEarnings(WALLET, null, 0, 20);

    assertThat(result.totalElements()).isEqualTo(3);
    assertThat(result.content()).extracting(EarningResponse::paymentId).containsExactly(2L, 3L, 1L);
    EarningResponse first = result.content().get(0);
    assertThat(first.status()).isEqualTo(EarningStatus.EXCLUDED);
    assertThat(first.amount()).isZero();
    assertThat(first.payableAt()).isNull();
    assertThat(first.exclusionReason()).isEqualTo(CashbackExclusionReason.MONTHLY_LIMIT_EXCEEDED);
    EarningResponse second = result.content().get(1);
    assertThat(second.status()).isEqualTo(EarningStatus.PENDING);
    assertThat(second.amount()).isEqualTo(300L);
    assertThat(second.appliedRate()).isEqualByComparingTo("0.03");
    assertThat(second.payableAt()).isEqualTo(LocalDateTime.of(2026, 11, 16, 0, 0));
    assertThat(second.exclusionReason()).isNull();
  }

  // 목적: 상태 필터가 해당 상태만 걸러 내는지(지급·취소 포함) 확인한다.
  @Test
  @DisplayName("상태로 걸러서 볼 수 있다")
  void listEarnings_filtersByStatus() {
    pending(1L, WALLET, LocalDateTime.of(2026, 11, 10, 9, 0));
    pending(2L, WALLET, LocalDateTime.of(2026, 11, 11, 9, 0));
    pending(3L, WALLET, LocalDateTime.of(2026, 11, 12, 9, 0));
    excluded(4L, WALLET, LocalDateTime.of(2026, 11, 13, 9, 0));
    setStatus(2L, "PAID");
    setStatus(3L, "CANCELED");

    assertThat(queryService.listEarnings(WALLET, EarningStatus.PENDING, 0, 20).content())
        .extracting(EarningResponse::paymentId)
        .containsExactly(1L);
    assertThat(queryService.listEarnings(WALLET, EarningStatus.PAID, 0, 20).content())
        .extracting(EarningResponse::paymentId)
        .containsExactly(2L);
    assertThat(queryService.listEarnings(WALLET, EarningStatus.CANCELED, 0, 20).content())
        .extracting(EarningResponse::paymentId)
        .containsExactly(3L);
    assertThat(queryService.listEarnings(WALLET, EarningStatus.EXCLUDED, 0, 20).content())
        .extracting(EarningResponse::paymentId)
        .containsExactly(4L);
    assertThat(queryService.listEarnings(WALLET, EarningStatus.CLAWED_BACK, 0, 20).content())
        .isEmpty();
  }

  // 목적: 페이지 나누기와 전체 건수, 너무 큰 페이지 크기가 상한으로 줄어드는지, 적립이 없으면 빈 목록인지 확인한다.
  @Test
  @DisplayName("페이지로 나누고 크기 상한을 지키며 없으면 빈 목록이다")
  void listEarnings_paging() {
    assertThat(queryService.listEarnings(WALLET, null, 0, 20).content()).isEmpty();
    for (long i = 1; i <= 5; i++) {
      pending(i, WALLET, LocalDateTime.of(2026, 11, (int) i, 9, 0));
    }

    PageResponse<EarningResponse> second = queryService.listEarnings(WALLET, null, 1, 2);

    assertThat(second.totalElements()).isEqualTo(5);
    assertThat(second.totalPages()).isEqualTo(3);
    assertThat(second.content()).extracting(EarningResponse::paymentId).containsExactly(3L, 2L);
    assertThat(queryService.listEarnings(WALLET, null, 0, 1_000).size())
        .isEqualTo(CashbackQueryService.MAX_PAGE_SIZE);
  }

  // 목적: 적립 건이 없는 이용자는 연·월(시계 기준)과 모든 값이 0 으로 나오는지 확인한다.
  @Test
  @DisplayName("적립이 없으면 요약은 모두 0 이다")
  void summary_empty() {
    assertThat(queryService.getSummary(WALLET))
        .isEqualTo(new CashbackSummaryResponse(2026, 11, 0L, 0L, 0L, 0L));
  }

  // 목적: 예정·지급 완료는 금액에 합산하고, 취소·회수는 금액에서 빼며, 제외는 건수로만 세는지 확인한다.
  @Test
  @DisplayName("예정과 지급 완료만 금액에 합산하고 제외는 건수로 센다")
  void summary_sumsByStatus() {
    pending(1L, WALLET, NOW); // 예정 300
    pending(2L, WALLET, NOW); // 예정 300
    pending(3L, WALLET, NOW); // 지급 완료 300
    pending(4L, WALLET, NOW); // 취소
    pending(5L, WALLET, NOW); // 회수
    excluded(6L, WALLET, NOW);
    excluded(7L, WALLET, NOW);
    setStatus(3L, "PAID");
    setStatus(4L, "CANCELED");
    setStatus(5L, "CLAWED_BACK");

    CashbackSummaryResponse result = queryService.getSummary(WALLET);

    assertThat(result.pendingAmount()).isEqualTo(600L);
    assertThat(result.paidAmount()).isEqualTo(300L);
    assertThat(result.earnedAmount()).isEqualTo(900L);
    assertThat(result.excludedCount()).isEqualTo(2L);
  }

  // 목적: 다른 이용자의 적립과 이번 달이 아닌 적립(접수 시각 기준, 월 경계 포함)이 섞이지 않는지 확인한다.
  @Test
  @DisplayName("다른 이용자와 다른 달의 적립은 섞이지 않고 월 경계는 접수 시각 기준이다")
  void summary_scopedToWalletAndMonth() {
    pending(1L, WALLET, LocalDateTime.of(2026, 11, 1, 0, 0)); // 이번 달 첫 순간: 포함
    pending(2L, WALLET, LocalDateTime.of(2026, 10, 31, 23, 59, 59)); // 지난달: 제외
    pending(3L, WALLET, LocalDateTime.of(2026, 12, 1, 0, 0)); // 다음 달: 제외
    pending(4L, WALLET, LocalDateTime.of(2026, 11, 30, 23, 59, 59)); // 이번 달 마지막 순간: 포함
    pending(5L, OTHER, NOW); // 다른 이용자: 제외
    excluded(6L, OTHER, NOW);

    CashbackSummaryResponse result = queryService.getSummary(WALLET);

    assertThat(result.pendingAmount()).isEqualTo(600L);
    assertThat(result.excludedCount()).isZero();
  }

  // 목적: 요약의 적립액이 같은 이용자의 적립 내역 목록(예정+지급 완료) 금액 합계와 일치하는지 확인한다.
  @Test
  @DisplayName("요약 적립액은 적립 내역 목록의 예정·지급 완료 합계와 같다")
  void summary_matchesEarningList() {
    pending(1L, WALLET, NOW);
    pending(2L, WALLET, NOW);
    pending(3L, WALLET, NOW);
    setStatus(2L, "PAID");
    setStatus(3L, "CANCELED");

    long listed =
        queryService.listEarnings(WALLET, null, 0, 100).content().stream()
            .filter(e -> e.status() == EarningStatus.PENDING || e.status() == EarningStatus.PAID)
            .mapToLong(EarningResponse::amount)
            .sum();

    assertThat(queryService.getSummary(WALLET).earnedAmount()).isEqualTo(listed).isEqualTo(600L);
  }
}
