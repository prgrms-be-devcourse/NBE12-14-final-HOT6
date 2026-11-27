package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.dto.SettingNoticeRequest;
import com.back.nbe1214finalhot6.setting.service.SettingChangeNotifier;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 설정 변경 공지를 공지 서비스로 등록한다. */
@Component
@RequiredArgsConstructor
public class CashbackSettingChangeNotifier implements SettingChangeNotifier {

  private final CashbackNoticeService noticeService;

  @Override
  public Long publish(
      SettingKey key,
      long previousValue,
      long newValue,
      LocalDate effectiveFrom,
      SettingNoticeRequest notice) {
    return noticeService.registerLimitChange(
        key.name(), previousValue, newValue, effectiveFrom, notice);
  }

  @Override
  public void withdraw(Long noticeId) {
    noticeService.withdraw(noticeId);
  }
}
