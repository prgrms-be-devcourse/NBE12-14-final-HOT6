package com.back.nbe1214finalhot6.setting.entity;

import com.back.nbe1214finalhot6.setting.SettingKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설정 변경 예약. 키당 한 행이다. 적용일이 되면 값을 읽을 때부터 예약 값을 쓰고, 운영자가 설정을 조회하거나 바꿀 때 설정 행에 반영하고 지운다.
 */
@Entity
@Table(name = "operator_setting_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatorSettingSchedule {

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "setting_key", nullable = false, length = 60)
  private SettingKey settingKey;

  // null 이면 기본값으로 되돌리는 예약
  @Column(name = "setting_value")
  private Long settingValue;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  @Column(name = "notice_id")
  private Long noticeId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  private OperatorSettingSchedule(
      SettingKey settingKey,
      Long settingValue,
      LocalDate effectiveDate,
      Long noticeId,
      LocalDateTime createdAt) {
    this.settingKey = settingKey;
    this.settingValue = settingValue;
    this.effectiveDate = effectiveDate;
    this.noticeId = noticeId;
    this.createdAt = createdAt;
  }

  /**
   * 예약을 만든다.
   *
   * @throws com.back.nbe1214finalhot6.common.exception.BusinessException 값이 키의 허용 범위를 벗어났을 때
   */
  public static OperatorSettingSchedule create(
      SettingKey settingKey,
      Long settingValue,
      LocalDate effectiveDate,
      Long noticeId,
      LocalDateTime now) {
    if (settingValue != null) {
      settingKey.validate(settingValue);
    }
    return new OperatorSettingSchedule(settingKey, settingValue, effectiveDate, noticeId, now);
  }

  /** 적용일이 되었는지. */
  public boolean isDue(LocalDate today) {
    return !effectiveDate.isAfter(today);
  }

  /** 적용일부터 쓰는 값. 기본값 예약이면 기본값이고, 하드 상한을 넘으면 상한으로 줄인다. */
  public long appliedValue() {
    return settingValue == null ? settingKey.defaultValue() : settingKey.capped(settingValue);
  }
}
