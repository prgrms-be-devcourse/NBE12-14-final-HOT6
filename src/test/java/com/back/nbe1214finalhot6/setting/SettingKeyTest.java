package com.back.nbe1214finalhot6.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.setting.exception.SettingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SettingKeyTest {

  // 목적: 모든 키의 기본값이 자기 허용 범위(최솟값 이상, 하드 상한 이하) 안에 있는지 확인한다. 범위 밖 기본값은 설정 오류다.
  @ParameterizedTest
  @EnumSource(SettingKey.class)
  @DisplayName("모든 키의 기본값은 허용 범위 안에 있다")
  void defaultValue_isWithinRange(SettingKey key) {
    assertThat(key.defaultValue()).isBetween(key.minValue(), key.hardMax());
    assertThatCode(() -> key.validate(key.defaultValue())).doesNotThrowAnyException();
  }

  // 목적: 합의한 기본값과 하드 상한이 그대로 정의되어 있는지 확인한다. 값이 바뀌면 기획서와 함께 고쳐야 한다.
  @Test
  @DisplayName("키별 기본값과 하드 상한은 합의한 값이다")
  void definitions_matchAgreedValues() {
    assertThat(SettingKey.CASHBACK_USER_MONTHLY_LIMIT.defaultValue()).isEqualTo(50_000L);
    assertThat(SettingKey.CASHBACK_USER_MONTHLY_LIMIT.hasHardMax()).isFalse();

    assertThat(SettingKey.WALLET_HOLDING_LIMIT.defaultValue()).isEqualTo(2_000_000L);
    assertThat(SettingKey.WALLET_HOLDING_LIMIT.hardMax()).isEqualTo(2_000_000L);

    assertThat(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT.defaultValue()).isEqualTo(1_000_000L);
    assertThat(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT.hardMax()).isEqualTo(1_000_000L);
  }

  // 목적: 최솟값 미만과 하드 상한 초과는 SET-001 로 거부하고, 경계값(최솟값, 상한과 같은 값)은 허용하는지 확인한다.
  @Test
  @DisplayName("범위를 벗어난 값은 거부하고 경계값은 허용한다")
  void validate_boundaries() {
    SettingKey key = SettingKey.WALLET_HOLDING_LIMIT;

    assertThatCode(() -> key.validate(key.minValue())).doesNotThrowAnyException();
    assertThatCode(() -> key.validate(key.hardMax())).doesNotThrowAnyException();
    for (long bad : new long[] {key.minValue() - 1, 0L, -1L, key.hardMax() + 1}) {
      assertThatThrownBy(() -> key.validate(bad))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("errorCode", SettingErrorCode.INVALID_SETTING);
    }
  }

  // 목적: 상한이 없는 키는 매우 큰 값도 허용하지만 최솟값 미만은 거부하는지 확인한다.
  @Test
  @DisplayName("상한이 없는 키는 큰 값을 허용하고 최솟값 미만만 거부한다")
  void validate_withoutHardMax() {
    SettingKey key = SettingKey.CASHBACK_USER_MONTHLY_LIMIT;

    assertThatCode(() -> key.validate(Long.MAX_VALUE)).doesNotThrowAnyException();
    assertThatThrownBy(() -> key.validate(0L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SettingErrorCode.INVALID_SETTING);
  }

  // 목적: 하드 상한을 넘는 값은 상한으로 줄이고, 상한 이하이거나 상한이 없는 키의 값은 그대로 두는지 확인한다.
  @Test
  @DisplayName("상한을 넘는 값만 상한으로 줄인다")
  void capped() {
    assertThat(SettingKey.WALLET_HOLDING_LIMIT.capped(3_000_000L)).isEqualTo(2_000_000L);
    assertThat(SettingKey.WALLET_HOLDING_LIMIT.capped(1_500_000L)).isEqualTo(1_500_000L);
    assertThat(SettingKey.CASHBACK_USER_MONTHLY_LIMIT.capped(99_999_999L)).isEqualTo(99_999_999L);
  }
}
