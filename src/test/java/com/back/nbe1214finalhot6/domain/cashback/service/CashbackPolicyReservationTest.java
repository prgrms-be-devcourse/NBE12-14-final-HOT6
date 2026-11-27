package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyHistoryResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateNoticeRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReservationResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.entity.PolicyHistoryAction;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyRepository;
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
import org.springframework.transaction.annotation.Transactional;

/** 인하 공지 작성과 예약한 인하의 수정. 테스트 시계는 2026-11-18 09:00 KST, 11월 기본 요율은 5% 다. */
@IntegrationTest
@Transactional
class CashbackPolicyReservationTest {

  private static final long OPERATOR_ID = 99L;
  private static final LocalDateTime NOV_20 = LocalDateTime.of(2026, 11, 20, 0, 0);
  private static final LocalDateTime NOV_25 = LocalDateTime.of(2026, 11, 25, 0, 0);
  private static final LocalDateTime DEC_1 = LocalDateTime.of(2026, 12, 1, 0, 0);

  @Autowired private CashbackPolicyService policyService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackNoticeService noticeService;
  @Autowired private CashbackPolicyRepository policyRepository;
  @Autowired private MutableClock clock;

  @BeforeEach
  void setUp() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.05")));
  }

  private static RateNoticeRequest notice(String title, LocalDate homeUntil) {
    return new RateNoticeRequest(title, "본문", true, homeUntil);
  }

  private static RateReductionRequest request(
      String rate, LocalDateTime at, String reason, RateNoticeRequest notice) {
    return new RateReductionRequest(new BigDecimal(rate), at, reason, notice);
  }

  private PolicyResponse reserve() {
    return policyService.reduceRate(
        request("0.03", NOV_20, "첫 사유", notice("첫 공지", LocalDate.of(2026, 11, 25))),
        OPERATOR_ID);
  }

  private NoticeResponse onlyNotice() {
    assertThat(noticeService.list(null, 0, 20).content()).hasSize(1);
    return noticeService.list(null, 0, 20).content().get(0);
  }

  // 목적: 인하할 때 운영자가 쓴 문구와 홈 노출로 공지가 게시되고 수정 표시는 없는지 확인한다.
  @Test
  @DisplayName("인하하면 작성한 공지가 게시된다")
  void reduceWithNotice() {
    PolicyResponse reserved = reserve();

    NoticeResponse published = onlyNotice();
    assertThat(published.title()).isEqualTo("첫 공지");
    assertThat(published.content()).isEqualTo("본문");
    assertThat(published.updatedAt()).isNull();
    assertThat(policyService.getReservation(reserved.policyId()))
        .extracting(RateReservationResponse::reason, r -> r.notice().homeUntil())
        .containsExactly("첫 사유", LocalDate.of(2026, 11, 25));
  }

  // 목적: 홈 종료일이 적용일보다 이르면 인하까지 거절되는지 확인한다.
  @Test
  @DisplayName("홈 종료일이 적용일보다 이르면 인하하지 않는다")
  void reduceRejectsEarlyHomeUntil() {
    assertError(
        () ->
            policyService.reduceRate(
                request("0.03", NOV_20, "사유", notice("공지", LocalDate.of(2026, 11, 19))),
                OPERATOR_ID),
        CashbackErrorCode.INVALID_NOTICE);
  }

  // 목적: 요율, 적용 시각, 사유, 공지가 바뀌고 새 이력에 사유와 근거가 남으며 첫 이력은 그대로인지 확인한다. 월 1회 제한에 걸리지 않아야 한다.
  @Test
  @DisplayName("예약을 수정하면 값과 공지가 바뀌고 새 이력이 남는다")
  void revise() {
    Long id = reserve().policyId();

    PolicyResponse revised =
        policyService.reviseReduction(
            id,
            request("0.04", NOV_25, "고친 사유", notice("고친 공지", LocalDate.of(2026, 11, 30))),
            OPERATOR_ID);

    assertThat(revised.rate()).isEqualByComparingTo("0.04");
    assertThat(revised.startsAt()).isEqualTo(NOV_25);
    assertThat(revised.endsAt()).isEqualTo(DEC_1);
    assertThat(policyRepository.count()).isEqualTo(1);
    NoticeResponse published = onlyNotice();
    assertThat(published.title()).isEqualTo("고친 공지");
    assertThat(published.effectiveFrom()).isEqualTo(NOV_25.toLocalDate());
    assertThat(published.rate()).isEqualByComparingTo("0.04");
    assertThat(published.updatedAt()).isNotNull();
    assertThat(policyService.findHistory(id, 0, 20).content())
        .extracting(
            PolicyHistoryResponse::action,
            PolicyHistoryResponse::reason,
            PolicyHistoryResponse::changedBy,
            h -> h.basis().previousRate().stripTrailingZeros().toPlainString())
        .containsExactly(
            tuple(PolicyHistoryAction.UPDATE, "고친 사유", OPERATOR_ID, "0.05"),
            tuple(PolicyHistoryAction.CREATE, "첫 사유", OPERATOR_ID, "0.05"));
  }

  // 목적: 앞 구간에 이어 시작하는 예약의 시각을 옮기면 앞 구간의 종료도 따라가고, 요율은 앞 구간보다 낮아야 하는지 확인한다.
  @Test
  @DisplayName("시각을 옮기면 앞 구간의 종료도 옮기고 앞 요율보다 낮아야 한다")
  void reviseMovesPrecedingEnd() {
    CashbackPolicy preceding =
        policyRepository.save(
            CashbackPolicy.create(
                new BigDecimal("0.04"),
                LocalDateTime.of(2026, 10, 31, 0, 0),
                DEC_1,
                LocalDateTime.of(2026, 10, 30, 9, 0)));
    Long id = reserve().policyId();

    assertError(
        () -> policyService.reviseReduction(id, request("0.04", NOV_25, "사유", null), OPERATOR_ID),
        CashbackErrorCode.INVALID_POLICY);
    policyService.reviseReduction(id, request("0.02", NOV_25, "사유", null), OPERATOR_ID);

    assertThat(preceding.getEndsAt()).isEqualTo(NOV_25);
    assertThat(policyService.effectiveRate(NOV_20)).contains(new BigDecimal("0.0400"));
    assertThat(policyService.effectiveRate(NOV_25)).contains(new BigDecimal("0.0200"));
  }

  // 목적: 다른 달로 옮기기, 지난 시각, 기본 요율 이상은 거절하고 값이 그대로인지 확인한다.
  @Test
  @DisplayName("다른 달, 지난 시각, 높은 요율로는 수정할 수 없다")
  void reviseRejectsInvalid() {
    Long id = reserve().policyId();

    assertError(
        () -> policyService.reviseReduction(id, request("0.02", DEC_1, "사유", null), OPERATOR_ID),
        CashbackErrorCode.INVALID_POLICY);
    assertError(
        () ->
            policyService.reviseReduction(
                id, request("0.02", LocalDateTime.of(2026, 11, 18, 8, 0), "사유", null), OPERATOR_ID),
        CashbackErrorCode.INVALID_POLICY);
    assertError(
        () -> policyService.reviseReduction(id, request("0.05", NOV_20, "사유", null), OPERATOR_ID),
        CashbackErrorCode.INVALID_POLICY);
    assertThat(policyService.get(id).rate()).isEqualByComparingTo("0.03");
  }

  // 목적: 적용이 시작된 구간은 조회도 수정도 CSH-017 이고, 없는 구간은 CSH-001 인지 확인한다.
  @Test
  @DisplayName("적용이 시작되면 수정할 수 없다")
  void reviseAfterStart() {
    Long id = reserve().policyId();
    clock.advance(Duration.ofDays(2));

    assertError(() -> policyService.getReservation(id), CashbackErrorCode.POLICY_ALREADY_APPLIED);
    assertError(
        () -> policyService.reviseReduction(id, request("0.02", NOV_25, "사유", null), OPERATOR_ID),
        CashbackErrorCode.POLICY_ALREADY_APPLIED);
    assertError(() -> policyService.getReservation(-1L), CashbackErrorCode.POLICY_NOT_FOUND);
  }

  private static void assertError(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(expected);
  }
}
