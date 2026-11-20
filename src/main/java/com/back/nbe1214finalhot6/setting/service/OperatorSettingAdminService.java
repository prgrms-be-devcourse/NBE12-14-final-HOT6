package com.back.nbe1214finalhot6.setting.service;

import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.dto.SettingResponse;
import com.back.nbe1214finalhot6.setting.entity.OperatorSetting;
import com.back.nbe1214finalhot6.setting.entity.OperatorSettingHistory;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingHistoryRepository;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자가 설정을 조회하고 바꾸는 서비스. 값 읽기는 {@link OperatorSettingService} 를 쓴다.
 *
 * <p>값을 바꿔도 이미 기록된 적립·충전은 건드리지 않고, 이후 읽기부터 새 값이 적용된다. 변경자는 인증이 머지되기 전까지 null 이다.
 */
@Service
@RequiredArgsConstructor
public class OperatorSettingAdminService {

  private final OperatorSettingRepository settingRepository;
  private final OperatorSettingHistoryRepository historyRepository;
  private final Clock clock;

  /** 모든 키의 현재 값을 키 정의 순서로 돌려준다. 운영자가 바꾼 적이 없는 키는 기본값이다. */
  @Transactional(readOnly = true)
  public List<SettingResponse> list() {
    Map<SettingKey, OperatorSetting> stored =
        settingRepository.findAll().stream()
            .collect(Collectors.toMap(OperatorSetting::getSettingKey, Function.identity()));
    return Arrays.stream(SettingKey.values())
        .map(key -> SettingResponse.of(key, stored.get(key)))
        .toList();
  }

  /**
   * 설정 값을 바꾼다. 지금 적용되는 값과 같은 값이면 아무것도 바꾸지 않고(행도 이력도 만들지 않고) 현재 상태를 돌려준다.
   *
   * <p>같은 키를 동시에 바꿀 때 변경 전 값이 낡은 값으로 이력에 남지 않도록 행을 잠그고 읽는다. 행이 아직 없을 때 두 요청이 동시에 처음 설정하면 한쪽은
   * 유일 제약으로 실패한다. 운영자 단독 작업을 전제로 따로 처리하지 않는다.
   *
   * @throws com.back.nbe1214finalhot6.common.exception.BusinessException 없는 키이거나 값이 허용 범위를 벗어났을 때
   *     (SET-001)
   */
  @Transactional
  public SettingResponse put(String keyName, long value) {
    SettingKey key = SettingKey.from(keyName);
    key.validate(value);
    OperatorSetting stored = settingRepository.findWithLockBySettingKey(key).orElse(null);
    long current = stored != null ? stored.getSettingValue() : key.defaultValue();
    if (current == value) {
      return SettingResponse.of(key, stored);
    }

    LocalDateTime now = LocalDateTime.now(clock);
    if (stored == null) {
      stored = settingRepository.save(OperatorSetting.create(key, value, null, now));
    } else {
      stored.changeValue(value, null, now);
    }
    historyRepository.save(OperatorSettingHistory.changed(key, current, value, null, now));
    return SettingResponse.of(key, stored);
  }

  /**
   * 운영자 설정을 지워서 기본값으로 되돌린다. 운영자가 바꾼 적이 없는 키면 아무것도 바꾸지 않는다.
   *
   * @throws com.back.nbe1214finalhot6.common.exception.BusinessException 없는 키일 때(SET-001)
   */
  @Transactional
  public SettingResponse reset(String keyName) {
    SettingKey key = SettingKey.from(keyName);
    OperatorSetting stored = settingRepository.findWithLockBySettingKey(key).orElse(null);
    if (stored == null) {
      return SettingResponse.of(key, null);
    }

    historyRepository.save(
        OperatorSettingHistory.reset(
            key, stored.getSettingValue(), null, LocalDateTime.now(clock)));
    settingRepository.delete(stored);
    return SettingResponse.of(key, null);
  }
}
