package com.back.nbe1214finalhot6.setting.service;

import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.entity.OperatorSettingSchedule;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingRepository;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingScheduleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자 설정 읽기. 다른 도메인은 이 서비스로만 설정 값을 읽는다. */
@Service
@RequiredArgsConstructor
public class OperatorSettingService {

  private final OperatorSettingRepository settingRepository;
  private final OperatorSettingScheduleRepository scheduleRepository;
  private final Clock clock;

  /**
   * 설정 값을 읽는다. 적용일이 지난 예약이 있으면 예약 값이고, 없으면 운영자가 정한 값이며, 바꾼 적이 없으면 키의 기본값이다.
   *
   * <p>저장된 값이 하드 상한을 넘으면(상한이 나중에 낮아진 경우) 상한으로 줄여서 돌려준다. 호출한 쪽의 트랜잭션에 참여하며 행 잠금은 잡지 않는다.
   */
  @Transactional(readOnly = true)
  public long getLong(SettingKey key) {
    Optional<OperatorSettingSchedule> due =
        scheduleRepository.findById(key).filter(schedule -> schedule.isDue(LocalDate.now(clock)));
    if (due.isPresent()) {
      return due.get().appliedValue();
    }
    return settingRepository
        .findById(key)
        .map(setting -> key.capped(setting.getSettingValue()))
        .orElse(key.defaultValue());
  }
}
