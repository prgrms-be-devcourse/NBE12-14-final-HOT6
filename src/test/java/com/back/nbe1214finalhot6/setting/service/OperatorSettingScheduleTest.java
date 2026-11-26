package com.back.nbe1214finalhot6.setting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeType;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackNoticeRepository;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackNoticeService;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.dto.SettingNoticeRequest;
import com.back.nbe1214finalhot6.setting.dto.SettingResponse;
import com.back.nbe1214finalhot6.setting.dto.SettingScheduleRequest;
import com.back.nbe1214finalhot6.setting.dto.SettingSource;
import com.back.nbe1214finalhot6.setting.entity.OperatorSettingHistory;
import com.back.nbe1214finalhot6.setting.exception.SettingErrorCode;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingHistoryRepository;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingScheduleRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 한도 변경의 공지와 예약. 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class OperatorSettingScheduleTest {

  private static final SettingKey KEY = SettingKey.CASHBACK_USER_MONTHLY_LIMIT;
  private static final String NAME = KEY.name();
  private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 19);
  private static final SettingNoticeRequest NOTICE =
      new SettingNoticeRequest("한도 변경", "월 적립 한도가 바뀌어요", false, null);

  @Autowired private OperatorSettingAdminService adminService;
  @Autowired private OperatorSettingService settingService;
  @Autowired private OperatorSettingScheduleRepository scheduleRepository;
  @Autowired private OperatorSettingHistoryRepository historyRepository;
  @Autowired private CashbackNoticeService noticeService;
  @Autowired private CashbackNoticeRepository noticeRepository;
  @Autowired private MutableClock clock;

  private SettingResponse schedule(Long value, LocalDate date) {
    return adminService.schedule(NAME, new SettingScheduleRequest(value, date, NOTICE));
  }

  private void assertError(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(expected);
  }

  private void nextDay() {
    clock.advance(Duration.ofDays(1));
  }

  // 목적: 예약하면 지금 값은 그대로이고 응답에 예약이 담기며, 이전·새 값과 적용일을 가진 공지가 게시되는지 확인한다.
  @Test
  @DisplayName("예약하면 값은 그대로이고 공지가 게시된다")
  void schedule_keepsValueAndPublishesNotice() {
    SettingResponse response = schedule(40_000L, TOMORROW);

    assertThat(response.value()).isEqualTo(50_000L);
    assertThat(response.scheduled().value()).isEqualTo(40_000L);
    assertThat(response.scheduled().reset()).isFalse();
    assertThat(response.scheduled().effectiveDate()).isEqualTo(TOMORROW);
    assertThat(settingService.getLong(KEY)).isEqualTo(50_000L);
    assertThat(noticeService.list(null, 0, 20).content())
        .singleElement()
        .satisfies(
            n -> {
              assertThat(n.noticeId()).isEqualTo(response.scheduled().noticeId());
              assertThat(n.type()).isEqualTo(NoticeType.LIMIT_CHANGE);
              assertThat(n.settingKey()).isEqualTo(NAME);
              assertThat(n.previousValue()).isEqualTo(50_000L);
              assertThat(n.newValue()).isEqualTo(40_000L);
              assertThat(n.effectiveFrom()).isEqualTo(TOMORROW);
            });
  }

  // 목적: 적용일이 되면 아무 작업 없이 읽는 값이 바뀌고, 조회할 때 설정 행에 반영되며 이력 시각이 적용일 00:00 인지 확인한다.
  @Test
  @DisplayName("적용일부터 예약 값을 읽고 조회할 때 반영한다")
  void due_appliesOnRead() {
    schedule(40_000L, TOMORROW);
    nextDay();

    assertThat(settingService.getLong(KEY)).isEqualTo(40_000L);
    assertThat(scheduleRepository.count()).isEqualTo(1);

    SettingResponse listed = adminService.list().get(0);

    assertThat(listed.value()).isEqualTo(40_000L);
    assertThat(listed.source()).isEqualTo(SettingSource.OPERATOR);
    assertThat(listed.scheduled()).isNull();
    assertThat(scheduleRepository.count()).isZero();
    assertThat(historyRepository.findAllBySettingKeyOrderByIdAsc(KEY))
        .singleElement()
        .satisfies(
            h -> {
              assertThat(h.getAfterValue()).isEqualTo(40_000L);
              assertThat(h.getChangedAt()).isEqualTo(LocalDateTime.of(2026, 11, 19, 0, 0));
            });
    assertThat(settingService.getLong(KEY)).isEqualTo(40_000L);
  }

  // 목적: 기본값으로 되돌리는 예약은 적용일부터 기본값이 되는지 확인한다.
  @Test
  @DisplayName("기본값 예약은 적용일부터 기본값이다")
  void scheduleReset() {
    adminService.put(NAME, 30_000L);

    SettingResponse response = schedule(null, TOMORROW);
    assertThat(response.scheduled().reset()).isTrue();
    assertThat(response.scheduled().value()).isEqualTo(50_000L);
    assertThat(settingService.getLong(KEY)).isEqualTo(30_000L);

    nextDay();
    assertThat(settingService.getLong(KEY)).isEqualTo(50_000L);
    assertThat(adminService.list().get(0).source()).isEqualTo(SettingSource.DEFAULT);
  }

  // 목적: 내일보다 이른 날, 범위 밖 값, 지금과 같은 값, 두 번째 예약을 거절하고 공지를 만들지 않는지 확인한다.
  @Test
  @DisplayName("잘못된 예약과 중복 예약은 거절한다")
  void schedule_invalid() {
    assertError(
        () -> schedule(40_000L, LocalDate.of(2026, 11, 18)), SettingErrorCode.INVALID_SETTING);
    assertError(() -> schedule(0L, TOMORROW), SettingErrorCode.INVALID_SETTING);
    assertError(() -> schedule(50_000L, TOMORROW), SettingErrorCode.INVALID_SETTING);
    assertThat(noticeService.list(null, 0, 20).content()).isEmpty();

    schedule(40_000L, TOMORROW);

    assertError(
        () -> schedule(30_000L, TOMORROW.plusDays(1)), SettingErrorCode.SCHEDULE_ALREADY_EXISTS);
  }

  // 목적: 취소하면 예약이 지워지고 공지가 거둬져 이용자에게 보이지 않으며, 다시 예약할 수 있는지 확인한다.
  @Test
  @DisplayName("예약을 취소하면 공지를 거두고 다시 예약할 수 있다")
  void cancel() {
    Long noticeId = schedule(40_000L, TOMORROW).scheduled().noticeId();

    SettingResponse response = adminService.cancelSchedule(NAME);

    assertThat(response.scheduled()).isNull();
    assertThat(noticeService.list(null, 0, 20).content()).isEmpty();
    assertThat(noticeRepository.findById(noticeId))
        .get()
        .satisfies(n -> assertThat(n.getStatus()).isEqualTo(NoticeStatus.WITHDRAWN));
    assertError(() -> adminService.cancelSchedule(NAME), SettingErrorCode.SCHEDULE_NOT_FOUND);
    assertThat(schedule(40_000L, TOMORROW).scheduled()).isNotNull();
  }

  // 목적: 적용일이 지난 예약은 취소할 수 없는지 확인한다.
  @Test
  @DisplayName("적용된 예약은 취소할 수 없다")
  void cancel_afterDue() {
    schedule(40_000L, TOMORROW);
    nextDay();

    assertError(() -> adminService.cancelSchedule(NAME), SettingErrorCode.SCHEDULE_NOT_FOUND);
    assertThat(settingService.getLong(KEY)).isEqualTo(40_000L);
  }

  // 목적: 예약 중에 바로 바꾸면 지금은 바꾼 값이고, 예약은 남아 적용일에 예약 값이 되는지 확인한다.
  @Test
  @DisplayName("예약 중에 바로 바꿔도 적용일에는 예약 값이다")
  void immediateChangeKeepsSchedule() {
    schedule(40_000L, TOMORROW);

    SettingResponse changed = adminService.put(NAME, 45_000L);

    assertThat(changed.value()).isEqualTo(45_000L);
    assertThat(changed.scheduled().value()).isEqualTo(40_000L);
    nextDay();
    assertThat(settingService.getLong(KEY)).isEqualTo(40_000L);
  }

  // 목적: 적용일이 지난 뒤 바로 바꾸면 예약을 먼저 반영하고 그 값에서 바꾸는지 확인한다.
  @Test
  @DisplayName("지난 예약은 반영한 뒤 새 변경을 적용한다")
  void changeAfterDue() {
    schedule(40_000L, TOMORROW);
    nextDay();

    adminService.put(NAME, 60_000L, NOTICE);

    assertThat(settingService.getLong(KEY)).isEqualTo(60_000L);
    assertThat(historyRepository.findAllBySettingKeyOrderByIdAsc(KEY))
        .extracting(OperatorSettingHistory::getBeforeValue, OperatorSettingHistory::getAfterValue)
        .containsExactly(
            org.assertj.core.api.Assertions.tuple(50_000L, 40_000L),
            org.assertj.core.api.Assertions.tuple(40_000L, 60_000L));
    assertThat(noticeService.list(null, 0, 20).content())
        .extracting(NoticeResponse::previousValue)
        .contains(40_000L);
  }

  // 목적: 공지와 함께 바로 바꾸고 되돌리면 각각 공지가 생기고, 값이 같으면 공지도 없는지, 공지 없이 바꾸면 공지가 없는지 확인한다.
  @Test
  @DisplayName("공지는 요청했고 값이 바뀔 때만 만든다")
  void noticeOnlyWhenRequestedAndChanged() {
    adminService.put(NAME, 30_000L);
    adminService.put(NAME, 30_000L, NOTICE);
    assertThat(noticeService.list(null, 0, 20).content()).isEmpty();

    adminService.put(NAME, 35_000L, NOTICE);
    adminService.reset(NAME, NOTICE);

    assertThat(noticeService.list(null, 0, 20).content())
        .extracting(NoticeResponse::previousValue, NoticeResponse::newValue)
        .containsExactlyInAnyOrder(
            org.assertj.core.api.Assertions.tuple(30_000L, 35_000L),
            org.assertj.core.api.Assertions.tuple(35_000L, 50_000L));
  }

  // 목적: 공지 내용이 잘못되면 설정 변경도 함께 실패하는지 확인한다.
  @Test
  @DisplayName("공지가 잘못되면 변경하지 않는다")
  void invalidNotice() {
    SettingNoticeRequest bad = new SettingNoticeRequest("제목", "본문", true, null);

    assertError(
        () -> adminService.schedule(NAME, new SettingScheduleRequest(40_000L, TOMORROW, bad)),
        CashbackErrorCode.INVALID_NOTICE);
    assertThat(scheduleRepository.count()).isZero();
  }

  // 목적: 이력은 최근 변경순이고, 적용일이 지난 예약은 조회할 때 반영되어 적용일 00:00 로 남으며, 되돌림은 기본값으로 보이는지 확인한다.
  @Test
  @DisplayName("이력은 최근순이고 지난 예약도 담긴다")
  void history() {
    adminService.put(NAME, 30_000L);
    schedule(null, TOMORROW);
    assertThat(adminService.history(0, 20).content()).hasSize(1);

    nextDay();

    assertThat(adminService.history(0, 20).content())
        .extracting(
            h -> h.previousValue(), h -> h.value(), h -> h.reset(), h -> h.changedAt())
        .containsExactly(
            org.assertj.core.api.Assertions.tuple(
                30_000L, 50_000L, true, TOMORROW.atStartOfDay()),
            org.assertj.core.api.Assertions.tuple(
                50_000L, 30_000L, false, LocalDateTime.of(2026, 11, 18, 9, 0)));
  }
}
