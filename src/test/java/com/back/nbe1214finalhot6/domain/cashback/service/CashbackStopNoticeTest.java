package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeType;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적립 중단 상태 전이 때 등록되는 공지. 11월 배정 1,000,000 이면 공지선(대비금)은 100,000, 긴급선은 50,000(전체의 5%)이다. 테스트 시계는 2026-11-18 09:00
 * KST 이고 공지 후 중단 예약일은 판정일 + 3일이다.
 */
@IntegrationTest
@Transactional
class CashbackStopNoticeTest {

  private static final int YEAR = 2026;
  private static final int MONTH = 11;

  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackBudgetStateService stateService;
  @Autowired private CashbackNoticeService noticeService;
  @Autowired private Clock clock;

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(YEAR, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(YEAR, MONTH, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  private List<NoticeResponse> notices(NoticeType type) {
    return noticeService.list(type, 0, 100).content();
  }

  // 목적: 공지선 바로 위에서는 공지가 없고, 딱 공지선에 닿으면 소진 예고가 한 건 생기며 문구가 긴급선 %와 최대 일수를 쓰고 시각은 쓰지 않으며 중단 예정일이 예약
  // 시각의 날짜인지 확인한다.
  @Test
  @DisplayName("공지선에 닿으면 소진 예고가 한 건 생기고 중단 예정일이 예약 날짜와 같다")
  void noticeLine_registersBudgetNotice() {
    budgetService.reserveBudget(YEAR, MONTH, 899_999L);
    assertThat(notices(null)).isEmpty();

    budgetService.reserveBudget(YEAR, MONTH, 1L);

    List<NoticeResponse> found = notices(null);
    assertThat(found).hasSize(1);
    NoticeResponse notice = found.get(0);
    assertThat(notice.type()).isEqualTo(NoticeType.BUDGET_NOTICE);
    assertThat(notice.stopAt()).isEqualTo(LocalDate.of(2026, 11, 21));
    assertThat(notice.effectiveFrom()).isEqualTo(LocalDate.of(2026, 11, 18));
    assertThat(notice.content())
        .contains("남은 예산이 5% 이하이거나 최대 3일 뒤 적립이 중단될 수 있어요")
        .contains("결제는 정상 이용할 수 있어요")
        .doesNotContain("원")
        .doesNotContainPattern("[0-9]{1,2}:[0-9]{2}");
  }

  // 목적: 공지선을 건너뛰고 긴급선 아래로 내려가면 소진 예고 없이 긴급 중단 공지만 한 건 생기는지 확인한다.
  @Test
  @DisplayName("공지선을 건너뛰어 바로 중단되면 긴급 중단 공지만 생긴다")
  void skippingNoticeLine_registersEmergencyStopOnly() {
    budgetService.reserveBudget(YEAR, MONTH, 950_000L);

    assertThat(notices(NoticeType.BUDGET_NOTICE)).isEmpty();
    List<NoticeResponse> found = notices(NoticeType.EMERGENCY_STOP);
    assertThat(found).hasSize(1);
    assertThat(found.get(0).content()).isEqualTo("적립이 중단되었어요. 결제는 정상 이용할 수 있어요");
    assertThat(found.get(0).stopAt()).isNull();
  }

  // 목적: 공지 후 긴급선에 닿아 중단되면 소진 예고(공지 때)와 긴급 중단(중단 때)이 각각 한 건씩 모두 남는지 확인한다.
  @Test
  @DisplayName("공지 후 긴급선에 닿으면 소진 예고와 긴급 중단이 각각 한 건이다")
  void noticeThenEmergency() {
    budgetService.reserveBudget(YEAR, MONTH, 900_000L);
    budgetService.reserveBudget(YEAR, MONTH, 50_000L);

    assertThat(notices(NoticeType.BUDGET_NOTICE)).hasSize(1);
    assertThat(notices(NoticeType.EMERGENCY_STOP)).hasSize(1);
  }

  // 목적: 공지 후 예약 시각이 되어 중단되는 전이는 이미 알렸으므로 새 공지를 만들지 않는지 확인한다.
  @Test
  @DisplayName("예약 시각이 되어 중단되면 새 공지를 만들지 않는다")
  void scheduledStop_registersNothing() {
    budgetService.reserveBudget(YEAR, MONTH, 900_000L);
    assertThat(notices(null)).hasSize(1);
    ((MutableClock) clock).advance(Duration.ofDays(3));

    int stopped = stateService.stopDueScheduled();

    assertThat(stopped).isEqualTo(1);
    assertThat(notices(null)).hasSize(1);
    assertThat(notices(NoticeType.EMERGENCY_STOP)).isEmpty();
  }

  // 목적: 운영자 "공지 후 중단"이 소진 예고를 한 건 만들고 반복해도(이미 공지 상태) 공지가 늘지 않는지 확인한다.
  @Test
  @DisplayName("운영자 공지 후 중단은 소진 예고를 한 번만 등록한다")
  void manualNoticeStop() {
    stateService.noticeStopManually(YEAR, MONTH);
    stateService.noticeStopManually(YEAR, MONTH);

    List<NoticeResponse> found = notices(null);
    assertThat(found).hasSize(1);
    assertThat(found.get(0).type()).isEqualTo(NoticeType.BUDGET_NOTICE);
    assertThat(found.get(0).stopAt()).isEqualTo(LocalDate.of(2026, 11, 21));
  }

  // 목적: 운영자 긴급 중단이 긴급 중단 공지를 한 건 만들고, 반복 요청은 거절되며 공지가 늘지 않는지 확인한다.
  @Test
  @DisplayName("운영자 긴급 중단은 긴급 중단 공지를 한 번만 등록한다")
  void manualEmergencyStop() {
    stateService.emergencyStopManually(YEAR, MONTH);
    assertThatThrownBy(() -> stateService.emergencyStopManually(YEAR, MONTH))
        .isInstanceOf(BusinessException.class);

    assertThat(notices(null)).hasSize(1);
    assertThat(notices(NoticeType.EMERGENCY_STOP)).hasSize(1);
  }

  // 목적: 이미 공지·중단 상태인 달에서 추가 확보로 전이가 일어나지 않으면 공지도 늘지 않는지 확인한다.
  @Test
  @DisplayName("전이가 없으면 공지도 생기지 않는다")
  void noTransition_noNotice() {
    budgetService.reserveBudget(YEAR, MONTH, 900_000L); // 공지
    budgetService.reserveBudget(YEAR, MONTH, 10_000L); // 공지 상태 유지(긴급선 위)

    assertThat(notices(null)).hasSize(1);
  }
}
