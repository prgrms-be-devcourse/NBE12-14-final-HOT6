package com.back.nbe1214finalhot6.setting.dto;

import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.entity.OperatorSetting;
import com.back.nbe1214finalhot6.setting.entity.OperatorSettingSchedule;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 운영자 설정 한 건.
 *
 * @param value 지금 적용되는 값(하드 상한을 넘는 저장값은 상한으로 줄인 값)
 * @param source 값의 출처. 운영자가 바꾼 적이 없으면 {@code DEFAULT}
 * @param hardMax 하드 상한. 상한이 없으면 null
 * @param updatedAt 운영자가 마지막으로 바꾼 시각. 기본값을 쓰면 null
 * @param updatedBy 변경한 운영자 ID. 인증이 머지되기 전까지, 또는 기본값을 쓰면 null
 */
public record SettingResponse(
    SettingKey key,
    String description,
    long value,
    SettingSource source,
    long defaultValue,
    long minValue,
    Long hardMax,
    LocalDateTime updatedAt,
    Long updatedBy,
    ScheduledChange scheduled) {

  /**
   * 아직 적용되지 않은 예약.
   *
   * @param value 적용일부터 쓸 값(기본값으로 되돌리는 예약이면 기본값)
   * @param reset 기본값으로 되돌리는 예약인지
   * @param effectiveDate 적용일
   * @param noticeId 예약을 알린 공지 ID
   */
  public record ScheduledChange(long value, boolean reset, LocalDate effectiveDate, Long noticeId) {}

  /** 예약이 없는 응답. */
  public SettingResponse(
      SettingKey key,
      String description,
      long value,
      SettingSource source,
      long defaultValue,
      long minValue,
      Long hardMax,
      LocalDateTime updatedAt,
      Long updatedBy) {
    this(key, description, value, source, defaultValue, minValue, hardMax, updatedAt, updatedBy, null);
  }

  /** 키와 저장된 행(없으면 null)으로 응답을 만든다. */
  public static SettingResponse of(SettingKey key, OperatorSetting stored) {
    return of(key, stored, null);
  }

  /** 키, 저장된 행(없으면 null), 대기 중인 예약(없으면 null)으로 응답을 만든다. */
  public static SettingResponse of(
      SettingKey key, OperatorSetting stored, OperatorSettingSchedule schedule) {
    boolean overridden = stored != null;
    return new SettingResponse(
        key,
        key.description(),
        overridden ? key.capped(stored.getSettingValue()) : key.defaultValue(),
        overridden ? SettingSource.OPERATOR : SettingSource.DEFAULT,
        key.defaultValue(),
        key.minValue(),
        key.hasHardMax() ? key.hardMax() : null,
        overridden ? stored.getUpdatedAt() : null,
        overridden ? stored.getUpdatedBy() : null,
        schedule == null
            ? null
            : new ScheduledChange(
                schedule.appliedValue(),
                schedule.getSettingValue() == null,
                schedule.getEffectiveDate(),
                schedule.getNoticeId()));
  }
}
