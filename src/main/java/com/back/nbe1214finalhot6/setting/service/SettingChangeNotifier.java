package com.back.nbe1214finalhot6.setting.service;

import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.dto.SettingNoticeRequest;
import java.time.LocalDate;

/**
 * 설정 변경을 이용자에게 알리는 통로. 설정 패키지가 공지를 가진 도메인에 의존하지 않도록 인터페이스만 두고, 구현은 공지를 가진 쪽이 한다. 호출한 쪽의
 * 트랜잭션에 참여한다.
 */
public interface SettingChangeNotifier {

  /** 공지를 등록하고 ID 를 돌려준다. */
  Long publish(
      SettingKey key,
      long previousValue,
      long newValue,
      LocalDate effectiveFrom,
      SettingNoticeRequest notice);

  /** 취소된 예약의 공지를 거둔다. */
  void withdraw(Long noticeId);
}
