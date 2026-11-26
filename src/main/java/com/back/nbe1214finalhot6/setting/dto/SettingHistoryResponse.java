package com.back.nbe1214finalhot6.setting.dto;

import com.back.nbe1214finalhot6.setting.entity.OperatorSettingHistory;
import com.back.nbe1214finalhot6.setting.SettingKey;
import java.time.LocalDateTime;

/**
 * 설정 변경 이력 한 건.
 *
 * @param previousValue 바꾸기 전에 적용되던 값
 * @param value 바꾼 뒤의 값(기본값으로 되돌렸으면 기본값)
 * @param reset 기본값으로 되돌린 변경인지
 * @param changedAt 바뀐 시각. 예약이 적용된 변경은 적용일 00:00
 */
public record SettingHistoryResponse(
    Long historyId,
    SettingKey key,
    String description,
    long previousValue,
    long value,
    boolean reset,
    LocalDateTime changedAt) {

  public static SettingHistoryResponse from(OperatorSettingHistory history) {
    SettingKey key = history.getSettingKey();
    boolean reset = history.getAfterValue() == null;
    return new SettingHistoryResponse(
        history.getId(),
        key,
        key.description(),
        history.getBeforeValue(),
        reset ? key.defaultValue() : history.getAfterValue(),
        reset,
        history.getChangedAt());
  }
}
