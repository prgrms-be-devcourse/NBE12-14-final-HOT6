package com.back.nbe1214finalhot6.setting.entity;

import com.back.nbe1214finalhot6.setting.SettingKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운영자 설정 변경 이력. 값이 실제로 바뀔 때마다 변경 전후 값을 한 행으로 남기며 수정하거나 지우지 않는다.
 *
 * <p>변경 전 값은 그 시점에 적용되던 값이라 운영자가 바꾼 적이 없으면 기본값이다. 변경 후 값이 null 이면 기본값으로 되돌린 것이다. 변경자는 인증이 머지되기
 * 전까지 null 이다.
 */
@Entity
@Table(name = "operator_setting_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatorSettingHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "setting_key", nullable = false, length = 60)
  private SettingKey settingKey;

  @Column(name = "before_value", nullable = false)
  private long beforeValue;

  // null 이면 기본값으로 되돌린 것
  @Column(name = "after_value")
  private Long afterValue;

  @Column(name = "changed_by")
  private Long changedBy;

  @Column(name = "changed_at", nullable = false)
  private LocalDateTime changedAt;

  private OperatorSettingHistory(
      SettingKey settingKey,
      long beforeValue,
      Long afterValue,
      Long changedBy,
      LocalDateTime changedAt) {
    this.settingKey = settingKey;
    this.beforeValue = beforeValue;
    this.afterValue = afterValue;
    this.changedBy = changedBy;
    this.changedAt = changedAt;
  }

  /** 값을 바꾼 기록. */
  public static OperatorSettingHistory changed(
      SettingKey settingKey, long beforeValue, long afterValue, Long changedBy, LocalDateTime now) {
    return new OperatorSettingHistory(settingKey, beforeValue, afterValue, changedBy, now);
  }

  /** 기본값으로 되돌린 기록. */
  public static OperatorSettingHistory reset(
      SettingKey settingKey, long beforeValue, Long changedBy, LocalDateTime now) {
    return new OperatorSettingHistory(settingKey, beforeValue, null, changedBy, now);
  }
}
