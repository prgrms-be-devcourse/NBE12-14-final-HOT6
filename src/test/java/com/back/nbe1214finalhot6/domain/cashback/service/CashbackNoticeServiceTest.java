package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeType;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackNoticeRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 캐시백 공지 등록·조회. 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class CashbackNoticeServiceTest {

  private static final long OPERATOR_ID = 99L;

  @Autowired private CashbackNoticeService noticeService;
  @Autowired private CashbackPolicyService policyService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackNoticeRepository noticeRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  private PolicyResponse reduce(String rate, LocalDateTime effectiveAt) {
    return policyService.reduceRate(new RateReductionRequest(new BigDecimal(rate), effectiveAt, "테스트 사유"), OPERATOR_ID);
  }

  private void insertNotice(String type, String content, LocalDateTime createdAt) {
    jdbcTemplate.update(
        "insert into cashback_notice (type, content, effective_from, created_at) values (?, ?, ?, ?)",
        type,
        content,
        createdAt.toLocalDate(),
        createdAt);
  }

  // 목적: 요율을 인하하면 정책 변경 예고가 한 건 생기고 새 구간의 정책 ID·적용 시작일(날짜)·새 요율이 공지에 맞게 담기는지 확인한다.
  @Test
  @DisplayName("요율 인하를 저장하면 정책 변경 예고 공지가 한 건 생긴다")
  void reduceRate_registersNotice() {
    PolicyResponse reduced = reduce("0.02", LocalDateTime.of(2026, 11, 20, 12, 0));

    PageResponse<NoticeResponse> result = noticeService.list(null, 0, 20);

    assertThat(result.totalElements()).isEqualTo(1);
    NoticeResponse notice = result.content().get(0);
    assertThat(notice.type()).isEqualTo(NoticeType.POLICY_CHANGE);
    assertThat(notice.effectiveFrom()).isEqualTo(reduced.startsAt().toLocalDate());
    assertThat(notice.content())
        .isEqualTo("11월 20일부터 적립률이 2%로 바뀌어요. 이미 접수된 결제는 약속한 금액 그대로 적립돼요");
    assertThat(notice.stopAt()).isNull();
    assertThat(notice.createdAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 0));
    assertThat(noticeRepository.findAll().get(0).getPolicyId()).isEqualTo(reduced.policyId());
  }

  private long insertPolicy(String rate, LocalDateTime startsAt, LocalDateTime endsAt) {
    return jdbcTemplate.queryForObject(
        "insert into cashback_policy (rate, starts_at, ends_at, created_at) values (?, ?, ?, ?) returning id",
        Long.class,
        new BigDecimal(rate),
        startsAt,
        endsAt,
        startsAt);
  }

  private void insertPolicyNotice(long policyId, LocalDateTime startsAt) {
    jdbcTemplate.update(
        "insert into cashback_notice (type, content, effective_from, policy_id, created_at) values (?, ?, ?, ?, ?)",
        "POLICY_CHANGE",
        "예고",
        startsAt.toLocalDate(),
        policyId,
        startsAt);
  }

  // 목적: 그 달의 첫 인하는 새 요율과 직전 요율(그 달 월 예산의 기본 요율)이 공지에 함께 나오는지 확인한다.
  @Test
  @DisplayName("첫 인하 공지는 새 요율과 월 기본 요율을 직전 요율로 담는다")
  void policyChange_previousRateIsBaseRate() {
    reduce("0.02", LocalDateTime.of(2026, 11, 20, 12, 0));

    NoticeResponse notice = noticeService.list(null, 0, 20).content().get(0);

    assertThat(notice.rate()).isEqualByComparingTo("0.02");
    assertThat(notice.previousRate()).isEqualByComparingTo("0.03");
  }

  // 목적: 같은 달에 구간이 이어지면 직전 요율이 직전 구간의 요율이고, 구간이 달을 넘어 이어져도(달 경계) 새 달의 첫 인하는 새 달의 기본 요율을 직전 요율로 쓰는지
  // 확인한다.
  @Test
  @DisplayName("직전 요율은 같은 달의 직전 구간이고 달 경계를 넘지 않는다")
  void policyChange_previousRateChainAndMonthBoundary() {
    budgetService.putMonthly(2026, 12, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.04")));
    LocalDateTime nov10 = LocalDateTime.of(2026, 11, 10, 0, 0);
    LocalDateTime nov20 = LocalDateTime.of(2026, 11, 20, 0, 0);
    LocalDateTime dec1 = LocalDateTime.of(2026, 12, 1, 0, 0);
    LocalDateTime jan1 = LocalDateTime.of(2027, 1, 1, 0, 0);
    long first = insertPolicy("0.02", nov10, nov20);
    long second = insertPolicy("0.015", nov20, dec1);
    long december = insertPolicy("0.03", dec1, jan1);
    insertPolicyNotice(first, nov10);
    insertPolicyNotice(second, nov20);
    insertPolicyNotice(december, dec1);

    List<NoticeResponse> notices = noticeService.list(null, 0, 20).content();

    NoticeResponse forFirst = byRate(notices, "0.02");
    NoticeResponse forSecond = byRate(notices, "0.015");
    NoticeResponse forDecember = byRate(notices, "0.03");
    assertThat(forFirst.previousRate()).isEqualByComparingTo("0.03"); // 그 달 첫 구간: 월 기본 요율
    assertThat(forSecond.previousRate()).isEqualByComparingTo("0.02"); // 직전 구간
    assertThat(forDecember.previousRate()).isEqualByComparingTo("0.04"); // 11월 구간이 아니라 12월 기본 요율
  }

  private NoticeResponse byRate(List<NoticeResponse> notices, String rate) {
    return notices.stream()
        .filter(n -> n.rate() != null && n.rate().compareTo(new BigDecimal(rate)) == 0)
        .findFirst()
        .orElseThrow();
  }

  // 목적: 요율과 무관한 공지(정책이 연결되지 않은 공지)는 요율 필드가 null 인지 확인한다.
  @Test
  @DisplayName("정책이 없는 공지는 요율 필드가 null 이다")
  void nonPolicyNotice_ratesNull() {
    insertNotice("BUDGET_NOTICE", "소진 예고", LocalDateTime.of(2026, 11, 17, 9, 0));

    NoticeResponse notice = noticeService.list(null, 0, 20).content().get(0);

    assertThat(notice.rate()).isNull();
    assertThat(notice.previousRate()).isNull();
  }

  // 목적: 즉시 적용(적용 시각이 지금)인 인하도 공지를 남기는지, 예고 일수가 0 인 기본 설정에서 적용 시작일이 오늘이 되는지 확인한다.
  @Test
  @DisplayName("즉시 적용되는 인하도 공지를 남긴다")
  void reduceRate_immediate() {
    reduce("0.025", LocalDateTime.of(2026, 11, 18, 9, 0));

    NoticeResponse notice = noticeService.list(null, 0, 20).content().get(0);

    assertThat(notice.effectiveFrom()).isEqualTo(java.time.LocalDate.of(2026, 11, 18));
    assertThat(notice.content()).contains("2.5%");
  }

  // 목적: 인하가 거절되면(현재 요율보다 낮지 않음) 공지가 남지 않는지 확인한다.
  @Test
  @DisplayName("인하가 거절되면 공지가 남지 않는다")
  void reduceRate_rejected_noNotice() {
    assertThatThrownBy(() -> reduce("0.05", LocalDateTime.of(2026, 11, 20, 12, 0)))
        .isInstanceOf(BusinessException.class);

    assertThat(noticeRepository.count()).isZero();
  }

  // 목적: 최근 등록순으로 나오고, 같은 시각이면 나중에 등록한 것이 먼저이며, 종류 필터가 해당 종류만 거르는지 확인한다.
  @Test
  @DisplayName("최근 등록순으로 나오고 종류로 거를 수 있다")
  void list_orderAndTypeFilter() {
    LocalDateTime t = LocalDateTime.of(2026, 11, 18, 9, 0);
    insertNotice("POLICY_CHANGE", "첫 번째", t.minusDays(2));
    insertNotice("BUDGET_NOTICE", "두 번째", t.minusDays(1));
    insertNotice("EMERGENCY_STOP", "세 번째", t);
    insertNotice("POLICY_CHANGE", "네 번째(같은 시각)", t);

    assertThat(noticeService.list(null, 0, 20).content())
        .extracting(NoticeResponse::content)
        .containsExactly("네 번째(같은 시각)", "세 번째", "두 번째", "첫 번째");
    assertThat(noticeService.list(NoticeType.POLICY_CHANGE, 0, 20).content())
        .extracting(NoticeResponse::content)
        .containsExactly("네 번째(같은 시각)", "첫 번째");
    assertThat(noticeService.list(NoticeType.STOP_CANCELED, 0, 20).content()).isEmpty();
  }

  // 목적: 페이지 나누기와 전체 건수, 너무 큰 페이지 크기가 상한으로 줄어드는지, 공지가 없으면 빈 목록인지 확인한다.
  @Test
  @DisplayName("페이지로 나누고 크기 상한을 지키며 없으면 빈 목록이다")
  void list_paging() {
    assertThat(noticeService.list(null, 0, 20).content()).isEmpty();
    for (int i = 0; i < 5; i++) {
      insertNotice("BUDGET_NOTICE", "공지 " + i, LocalDateTime.of(2026, 11, 1 + i, 9, 0));
    }

    PageResponse<NoticeResponse> second = noticeService.list(null, 1, 2);

    assertThat(second.totalElements()).isEqualTo(5);
    assertThat(second.totalPages()).isEqualTo(3);
    assertThat(second.content()).extracting(NoticeResponse::content).containsExactly("공지 2", "공지 1");
    assertThat(noticeService.list(null, 0, 1_000).size()).isEqualTo(CashbackNoticeService.MAX_PAGE_SIZE);
  }
}
