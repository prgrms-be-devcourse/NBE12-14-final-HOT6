package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeAdminResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeUpdateRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeStatus;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.setting.dto.SettingNoticeRequest;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 운영자의 공지 조회와 수정. 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class CashbackNoticeEditTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

  @Autowired private CashbackNoticeService noticeService;
  @Autowired private MutableClock clock;

  private Long limitNotice(String title, boolean showOnHome, LocalDate homeUntil) {
    return noticeService.registerLimitChange(
        "CASHBACK_USER_MONTHLY_LIMIT",
        50_000L,
        40_000L,
        TODAY,
        new SettingNoticeRequest(title, "본문", showOnHome, homeUntil));
  }

  private static NoticeUpdateRequest request(
      String title, String content, boolean showOnHome, LocalDate homeUntil) {
    return new NoticeUpdateRequest(title, content, showOnHome, homeUntil);
  }

  // 목적: 문구를 고치면 이용자 화면에 반영되고 수정 시각이 남으며, 알리는 값은 그대로인지 확인한다.
  @Test
  @DisplayName("문구를 고치면 수정 시각이 남고 값은 바뀌지 않는다")
  void editText() {
    Long id = limitNotice("제목", false, null);

    noticeService.update(id, request("고친 제목", "고친 본문", false, null));

    assertThat(noticeService.list(null, 0, 20).content())
        .singleElement()
        .satisfies(
            n -> {
              assertThat(n.title()).isEqualTo("고친 제목");
              assertThat(n.content()).isEqualTo("고친 본문");
              assertThat(n.updatedAt()).isNotNull();
              assertThat(n.previousValue()).isEqualTo(50_000L);
              assertThat(n.newValue()).isEqualTo(40_000L);
            });
  }

  // 목적: 홈 노출만 바꾸면 수정됨 표시가 생기지 않고, 홈 목록에는 바로 반영되는지 확인한다.
  @Test
  @DisplayName("홈 노출만 바꾸면 수정 시각이 없다")
  void editHomeOnly() {
    Long id = limitNotice("제목", false, null);

    NoticeAdminResponse shown = noticeService.update(id, request("제목", "본문", true, TODAY));

    assertThat(shown.notice().updatedAt()).isNull();
    assertThat(noticeService.listForHome()).hasSize(1);

    noticeService.update(id, request("제목", "본문", false, null));
    assertThat(noticeService.listForHome()).isEmpty();
  }

  // 목적: 지난 종료일을 그대로 두면 문구를 고칠 수 있지만, 지난 날짜로 새로 바꾸는 것은 거절하는지 확인한다.
  @Test
  @DisplayName("지난 종료일은 유지할 수 있고 새로 지정할 수는 없다")
  void pastHomeUntil() {
    Long id = limitNotice("제목", true, TODAY);
    clock.advance(Duration.ofDays(2));

    noticeService.update(id, request("고친 제목", "본문", true, TODAY));

    assertInvalid(() -> noticeService.update(id, request("제목", "본문", true, TODAY.plusDays(1))));
  }

  // 목적: 운영자 목록은 거둔 공지도 보여 주지만 거둔 공지는 고칠 수 없고, 없는 공지는 CSH-015 인지 확인한다.
  @Test
  @DisplayName("거둔 공지는 조회만 되고 없는 공지는 CSH-015 다")
  void withdrawnAndMissing() {
    Long id = limitNotice("제목", false, null);
    noticeService.withdraw(id);

    assertThat(noticeService.listForAdmin(0, 20).content())
        .singleElement()
        .extracting(NoticeAdminResponse::status)
        .isEqualTo(NoticeStatus.WITHDRAWN);
    assertInvalid(() -> noticeService.update(id, request("제목", "본문", false, null)));
    assertThatThrownBy(() -> noticeService.update(-1L, request("제목", "본문", false, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CashbackErrorCode.NOTICE_NOT_FOUND);
  }

  private static void assertInvalid(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CashbackErrorCode.INVALID_NOTICE);
  }
}
