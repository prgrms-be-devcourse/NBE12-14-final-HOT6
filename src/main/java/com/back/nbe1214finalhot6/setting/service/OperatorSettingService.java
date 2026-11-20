package com.back.nbe1214finalhot6.setting.service;

import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자 설정 읽기. 다른 도메인은 이 서비스로만 설정 값을 읽는다. */
@Service
@RequiredArgsConstructor
public class OperatorSettingService {

  private final OperatorSettingRepository settingRepository;

  /**
   * 설정 값을 읽는다. 운영자가 바꾼 적이 없으면 키의 기본값을 돌려준다.
   *
   * <p>저장된 값이 하드 상한을 넘으면(상한이 나중에 낮아진 경우) 상한으로 줄여서 돌려준다. 호출한 쪽의 트랜잭션에 참여하며 행 잠금은 잡지 않는다.
   */
  @Transactional(readOnly = true)
  public long getLong(SettingKey key) {
    return settingRepository
        .findById(key)
        .map(setting -> key.capped(setting.getSettingValue()))
        .orElse(key.defaultValue());
  }
}
