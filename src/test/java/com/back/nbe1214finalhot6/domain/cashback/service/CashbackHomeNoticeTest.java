package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeType;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.setting.dto.SettingNoticeRequest;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 홈 공지의 조건과 공지 제목. 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class CashbackHomeNoticeTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

  @Autowired private CashbackNoticeService noticeService;
  @Autowired private CashbackPolicyService policyService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackBudgetStateService stateService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MutableClock clock;

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  private Long limitNotice(String title, boolean showOnHome, LocalDate homeUntil) {
    return noticeService.registerLimitChange(
        "CASHBACK_USER_MONTHLY_LIMIT",
        50_000L,
        40_000L,
        TODAY,
        new SettingNoticeRequest(title, "본문", showOnHome, homeUntil));
  }

  // 목적: 홈에 띄운 공지는 종료일 당일까지 보이고 다음 날부터 빠지며, 최근 5건까지만 주고, 띄우지 않은 공지는 목록에만 있는지 확인한다.
  @Test
  @DisplayName("홈 공지는 종료일까지의 최근 5건이다")
  void home_untilAndLimit() {
    limitNotice("목록에만", false, null);
    for (int i = 1; i <= 6; i++) {
      clock.advance(Duration.ofMinutes(1));
      limitNotice("공지" + i, true, TODAY.plusDays(1));
    }

    assertThat(noticeService.listForHome())
        .extracting(NoticeResponse::title)
        .containsExactly("공지6", "공지5", "공지4", "공지3", "공지2");
    assertThat(noticeService.list(null, 0, 20).content()).hasSize(7);

    clock.advance(Duration.ofDays(1));
    assertThat(noticeService.listForHome()).hasSize(5);
    clock.advance(Duration.ofDays(1));
    assertThat(noticeService.listForHome()).isEmpty();
  }

  // 목적: 거둔 공지는 홈과 목록에서 빠지는지 확인한다.
  @Test
  @DisplayName("거둔 공지는 보이지 않는다")
  void withdrawn() {
    Long id = limitNotice("제목", true, TODAY);

    noticeService.withdraw(id);

    assertThat(noticeService.listForHome()).isEmpty();
    assertThat(noticeService.list(null, 0, 20).content()).isEmpty();
  }

  // 목적: 홈에 띄우려면 오늘 이후의 종료일이 있어야 하고 제목·본문이 비거나 길면 거절하는지 확인한다.
  @Test
  @DisplayName("홈 종료일이나 내용이 잘못되면 CSH-014 다")
  void invalid() {
    for (Runnable action :
        new Runnable[] {
          () -> limitNotice("t", true, null),
          () -> limitNotice("t", true, TODAY.minusDays(1)),
          () -> limitNotice(" ", false, null),
          () -> limitNotice("가".repeat(101), false, null)
        }) {
      assertThatThrownBy(action::run)
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(CashbackErrorCode.INVALID_NOTICE);
    }
  }

  // 목적: 요율 인하와 긴급 중단의 자동 공지는 제목이 있고 홈에 보이며, 그 달 말일이 지나면 빠지는지 확인한다.
  @Test
  @DisplayName("자동 공지는 그 달 말일까지 홈에 보인다")
  void automaticNoticesShowOnHome() {
    policyService.reduceRate(
        new RateReductionRequest(new BigDecimal("0.02"), LocalDateTime.of(2026, 11, 20, 0, 0)));
    clock.advance(Duration.ofMinutes(1));
    stateService.emergencyStopManually(2026, 11);

    assertThat(noticeService.listForHome())
        .extracting(NoticeResponse::type, NoticeResponse::title)
        .containsExactly(
            tuple(NoticeType.EMERGENCY_STOP, "적립 중단 안내"),
            tuple(NoticeType.POLICY_CHANGE, "적립률 변경 안내"));

    clock.advance(Duration.ofDays(12));
    assertThat(noticeService.listForHome()).hasSize(2);
    clock.advance(Duration.ofDays(1));
    assertThat(noticeService.listForHome()).isEmpty();
  }

  // 목적: 제목 컬럼이 생기기 전에 저장된 공지는 기본 제목으로 보이고 홈에는 없는지 확인한다.
  @Test
  @DisplayName("제목 없는 기존 공지는 기본 제목으로 보인다")
  void oldNoticeGetsDefaultTitle() {
    jdbcTemplate.update(
        "insert into cashback_notice (type, content, effective_from, created_at) values (?, ?, ?, ?)",
        "EMERGENCY_STOP",
        "예전 본문",
        TODAY,
        LocalDateTime.of(2026, 11, 17, 9, 0));

    assertThat(noticeService.list(null, 0, 20).content())
        .singleElement()
        .satisfies(n -> assertThat(n.title()).isEqualTo("적립 중단 안내"));
    assertThat(noticeService.listForHome()).isEmpty();
  }
}
