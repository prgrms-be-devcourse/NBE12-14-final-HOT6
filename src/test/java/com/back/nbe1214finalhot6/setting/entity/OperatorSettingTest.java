package com.back.nbe1214finalhot6.setting.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.exception.SettingErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OperatorSettingTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 9, 0);

  // 목적: 허용 범위 안의 값으로 만들면 입력값이 그대로 담기는지 확인한다(변경자는 인증 머지 전이라 null 이다).
  @Test
  @DisplayName("허용 범위 안의 값으로 만든다")
  void create_valid() {
    OperatorSetting setting =
        OperatorSetting.create(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT, 800_000L, null, NOW);

    assertThat(setting.getSettingKey()).isEqualTo(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT);
    assertThat(setting.getSettingValue()).isEqualTo(800_000L);
    assertThat(setting.getUpdatedBy()).isNull();
    assertThat(setting.getUpdatedAt()).isEqualTo(NOW);
  }

  // 목적: 하드 상한을 넘거나 최솟값 미만인 값으로는 만들 수 없는지 확인한다.
  @Test
  @DisplayName("허용 범위를 벗어난 값으로는 만들 수 없다")
  void create_outOfRange() {
    assertThatThrownBy(
            () -> OperatorSetting.create(SettingKey.WALLET_HOLDING_LIMIT, 2_000_001L, null, NOW))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SettingErrorCode.INVALID_SETTING);
    assertThatThrownBy(
            () -> OperatorSetting.create(SettingKey.CASHBACK_USER_MONTHLY_LIMIT, 0L, null, NOW))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SettingErrorCode.INVALID_SETTING);
  }
}
