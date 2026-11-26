package com.back.nbe1214finalhot6.setting.service;

import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.dto.SettingNoticeRequest;
import com.back.nbe1214finalhot6.setting.dto.SettingResponse;
import com.back.nbe1214finalhot6.setting.dto.SettingScheduleRequest;
import com.back.nbe1214finalhot6.setting.entity.OperatorSetting;
import com.back.nbe1214finalhot6.setting.entity.OperatorSettingHistory;
import com.back.nbe1214finalhot6.setting.entity.OperatorSettingSchedule;
import com.back.nbe1214finalhot6.setting.exception.SettingErrorCode;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingHistoryRepository;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingRepository;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingScheduleRepository;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
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
 * <p>바꾸는 방법은 바로 바꾸기(공지는 선택)와 날짜 예약(공지 필수)이다. 값을 바꿔도 이미 기록된 적립·충전은 건드리지 않고, 이후 읽기부터 새 값이 적용된다.
 * 적용일이 지난 예약은 조회하거나 바꾸기 전에 설정 행에 반영하고 이력을 남긴다. 변경자는 인증이 머지되기 전까지 null 이다.
 */
@Service
@RequiredArgsConstructor
public class OperatorSettingAdminService {

  private final OperatorSettingRepository settingRepository;
  private final OperatorSettingHistoryRepository historyRepository;
  private final OperatorSettingScheduleRepository scheduleRepository;
  private final SettingChangeNotifier notifier;
  private final Clock clock;

  /** 모든 키의 현재 값과 대기 중인 예약을 키 정의 순서로 돌려준다. 운영자가 바꾼 적이 없는 키는 기본값이다. */
  @Transactional
  public List<SettingResponse> list() {
    Arrays.stream(SettingKey.values()).forEach(this::applyDueSchedule);
    Map<SettingKey, OperatorSetting> stored =
        settingRepository.findAll().stream()
            .collect(Collectors.toMap(OperatorSetting::getSettingKey, Function.identity()));
    Map<SettingKey, OperatorSettingSchedule> schedules =
        scheduleRepository.findAll().stream()
            .collect(Collectors.toMap(OperatorSettingSchedule::getSettingKey, Function.identity()));
    return Arrays.stream(SettingKey.values())
        .map(key -> SettingResponse.of(key, stored.get(key), schedules.get(key)))
        .toList();
  }

  /** 공지 없이 바로 바꾼다. */
  @Transactional
  public SettingResponse put(String keyName, long value) {
    return put(keyName, value, null);
  }

  /**
   * 설정 값을 바로 바꾼다. 지금 적용되는 값과 같은 값이면 아무것도 바꾸지 않고(행도 이력도 공지도 만들지 않고) 현재 상태를 돌려준다. 대기 중인 예약은 남는다.
   *
   * <p>같은 키를 동시에 바꿀 때 변경 전 값이 낡은 값으로 이력에 남지 않도록 행을 잠그고 읽는다. 행이 아직 없을 때 두 요청이 동시에 처음 설정하면 한쪽은
   * 유일 제약으로 실패한다. 운영자 단독 작업을 전제로 따로 처리하지 않는다.
   *
   * @param notice 함께 등록할 공지. null 이면 공지하지 않는다
   * @throws com.back.nbe1214finalhot6.common.exception.BusinessException 없는 키이거나 값이 허용 범위를 벗어났을 때
   *     (SET-001), 공지 내용이 올바르지 않을 때(CSH-014)
   */
  @Transactional
  public SettingResponse put(String keyName, long value, SettingNoticeRequest notice) {
    SettingKey key = SettingKey.from(keyName);
    key.validate(value);
    applyDueSchedule(key);
    OperatorSetting stored = settingRepository.findWithLockBySettingKey(key).orElse(null);
    long current = stored != null ? stored.getSettingValue() : key.defaultValue();
    if (current == value) {
      return response(key, stored);
    }

    LocalDateTime now = LocalDateTime.now(clock);
    if (stored == null) {
      stored = settingRepository.save(OperatorSetting.create(key, value, null, now));
    } else {
      stored.changeValue(value, null, now);
    }
    historyRepository.save(OperatorSettingHistory.changed(key, current, value, null, now));
    if (notice != null) {
      notifier.publish(key, key.capped(current), value, now.toLocalDate(), notice);
    }
    return response(key, stored);
  }

  /** 공지 없이 기본값으로 되돌린다. */
  @Transactional
  public SettingResponse reset(String keyName) {
    return reset(keyName, null);
  }

  /**
   * 운영자 설정을 지워서 바로 기본값으로 되돌린다. 운영자가 바꾼 적이 없는 키면 아무것도 바꾸지 않는다. 대기 중인 예약은 남는다.
   *
   * @param notice 함께 등록할 공지. null 이면 공지하지 않는다
   * @throws com.back.nbe1214finalhot6.common.exception.BusinessException 없는 키일 때(SET-001), 공지 내용이 올바르지
   *     않을 때(CSH-014)
   */
  @Transactional
  public SettingResponse reset(String keyName, SettingNoticeRequest notice) {
    SettingKey key = SettingKey.from(keyName);
    applyDueSchedule(key);
    OperatorSetting stored = settingRepository.findWithLockBySettingKey(key).orElse(null);
    if (stored == null) {
      return response(key, null);
    }

    LocalDateTime now = LocalDateTime.now(clock);
    long current = stored.getSettingValue();
    historyRepository.save(OperatorSettingHistory.reset(key, current, null, now));
    settingRepository.delete(stored);
    if (notice != null && key.capped(current) != key.defaultValue()) {
      notifier.publish(key, key.capped(current), key.defaultValue(), now.toLocalDate(), notice);
    }
    return response(key, null);
  }

  /**
   * 변경을 예약하고 공지한다. 설정마다 예약은 하나다.
   *
   * @throws com.back.nbe1214finalhot6.common.exception.BusinessException 없는 키, 범위를 벗어난 값, 내일보다 이른 적용일,
   *     지금과 같은 값일 때(SET-001), 이미 예약이 있을 때(SET-002), 공지 내용이 올바르지 않을 때(CSH-014)
   */
  @Transactional
  public SettingResponse schedule(String keyName, SettingScheduleRequest request) {
    SettingKey key = SettingKey.from(keyName);
    if (request.value() != null) {
      key.validate(request.value());
    }
    LocalDate today = LocalDate.now(clock);
    if (!request.effectiveDate().isAfter(today)) {
      throw new BusinessException(SettingErrorCode.INVALID_SETTING, "적용일은 내일 이후여야 합니다");
    }
    applyDueSchedule(key);
    if (scheduleRepository.existsById(key)) {
      throw new BusinessException(SettingErrorCode.SCHEDULE_ALREADY_EXISTS);
    }
    OperatorSetting stored = settingRepository.findWithLockBySettingKey(key).orElse(null);
    long current = stored != null ? key.capped(stored.getSettingValue()) : key.defaultValue();
    long next = request.value() != null ? request.value() : key.defaultValue();
    if (current == next) {
      throw new BusinessException(SettingErrorCode.INVALID_SETTING, "지금 적용되는 값과 같습니다");
    }
    Long noticeId =
        notifier.publish(key, current, next, request.effectiveDate(), request.notice());
    scheduleRepository.save(
        OperatorSettingSchedule.create(
            key, request.value(), request.effectiveDate(), noticeId, LocalDateTime.now(clock)));
    return response(key, stored);
  }

  /**
   * 아직 적용되지 않은 예약을 취소하고 그 공지를 거둔다.
   *
   * @throws com.back.nbe1214finalhot6.common.exception.BusinessException 없는 키(SET-001), 취소할 예약이 없을 때(SET-003)
   */
  @Transactional
  public SettingResponse cancelSchedule(String keyName) {
    SettingKey key = SettingKey.from(keyName);
    applyDueSchedule(key);
    OperatorSettingSchedule schedule =
        scheduleRepository
            .findWithLockBySettingKey(key)
            .orElseThrow(() -> new BusinessException(SettingErrorCode.SCHEDULE_NOT_FOUND));
    scheduleRepository.delete(schedule);
    if (schedule.getNoticeId() != null) {
      notifier.withdraw(schedule.getNoticeId());
    }
    return response(key, settingRepository.findById(key).orElse(null));
  }

  // 적용일이 지난 예약을 설정 행에 반영하고 지운다. 이력의 시각은 적용일 00:00 이다
  private void applyDueSchedule(SettingKey key) {
    LocalDate today = LocalDate.now(clock);
    if (scheduleRepository.findById(key).filter(s -> s.isDue(today)).isEmpty()) {
      return;
    }
    // 잠근 뒤 다시 확인한다. 다른 요청이 먼저 반영했으면 행이 없다
    OperatorSettingSchedule schedule =
        scheduleRepository.findWithLockBySettingKey(key).orElse(null);
    if (schedule == null || !schedule.isDue(today)) {
      return;
    }
    LocalDateTime at = schedule.getEffectiveDate().atStartOfDay();
    OperatorSetting stored = settingRepository.findWithLockBySettingKey(key).orElse(null);
    long current = stored != null ? stored.getSettingValue() : key.defaultValue();
    if (schedule.getSettingValue() == null) {
      if (stored != null) {
        historyRepository.save(OperatorSettingHistory.reset(key, current, null, at));
        settingRepository.delete(stored);
      }
    } else if (current != schedule.getSettingValue()) {
      if (stored == null) {
        settingRepository.save(
            OperatorSetting.create(key, schedule.appliedValue(), null, at));
      } else {
        stored.changeValue(schedule.appliedValue(), null, at);
      }
      historyRepository.save(
          OperatorSettingHistory.changed(key, current, schedule.appliedValue(), null, at));
    }
    scheduleRepository.delete(schedule);
    scheduleRepository.flush();
    settingRepository.flush();
  }

  private SettingResponse response(SettingKey key, OperatorSetting stored) {
    return SettingResponse.of(key, stored, scheduleRepository.findById(key).orElse(null));
  }
}
