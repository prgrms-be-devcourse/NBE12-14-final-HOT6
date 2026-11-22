package com.back.nbe1214finalhot6.setting.entity;

import com.back.nbe1214finalhot6.setting.SettingKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운영자가 바꾼 설정 값. 키당 한 행이고, 행이 없으면 {@link SettingKey#defaultValue()} 를 쓴다.
 */
@Entity
@Table(name = "operator_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatorSetting {

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "setting_key", nullable = false, length = 60)
  private SettingKey settingKey;

  @Column(name = "setting_value", nullable = false)
  private long settingValue;

  // 인증이 머지되기 전까지 null
  @Column(name = "updated_by")
  private Long updatedBy;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  private OperatorSetting(
      SettingKey settingKey, long settingValue, Long updatedBy, LocalDateTime updatedAt) {
    this.settingKey = settingKey;
    this.settingValue = settingValue;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
  }

  /**
   * 설정 값을 만든다.
   *
   * @throws com.back.nbe1214finalhot6.common.exception.BusinessException 값이 키의 허용 범위를 벗어났을 때
   */
  public static OperatorSetting create(
      SettingKey settingKey, long settingValue, Long updatedBy, LocalDateTime now) {
    settingKey.validate(settingValue);
    return new OperatorSetting(settingKey, settingValue, updatedBy, now);
  }

  /**
   * 값을 바꾼다.
   *
   * @throws com.back.nbe1214finalhot6.common.exception.BusinessException 값이 키의 허용 범위를 벗어났을 때
   */
  public void changeValue(long settingValue, Long updatedBy, LocalDateTime now) {
    settingKey.validate(settingValue);
    this.settingValue = settingValue;
    this.updatedBy = updatedBy;
    this.updatedAt = now;
  }
}
