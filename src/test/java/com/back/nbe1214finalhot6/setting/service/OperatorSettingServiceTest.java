package com.back.nbe1214finalhot6.setting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.entity.OperatorSetting;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class OperatorSettingServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 9, 0);

  @Autowired private OperatorSettingService settingService;
  @Autowired private OperatorSettingRepository settingRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @PersistenceContext private EntityManager entityManager;

  // 목적: 운영자가 값을 바꾼 적이 없으면(행이 없으면) 모든 키가 기본값을 돌려주는지 확인한다.
  @ParameterizedTest
  @EnumSource(SettingKey.class)
  @DisplayName("행이 없으면 기본값을 돌려준다")
  void getLong_defaultWhenNoRow(SettingKey key) {
    assertThat(settingService.getLong(key)).isEqualTo(key.defaultValue());
  }

  // 목적: 행이 있으면 그 값을 돌려주고, 다른 키에는 영향을 주지 않는지 확인한다.
  @Test
  @DisplayName("행이 있으면 저장된 값을 돌려주고 다른 키는 기본값이다")
  void getLong_storedValue() {
    settingRepository.save(
        OperatorSetting.create(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT, 700_000L, null, NOW));

    assertThat(settingService.getLong(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT)).isEqualTo(700_000L);
    assertThat(settingService.getLong(SettingKey.WALLET_HOLDING_LIMIT))
        .isEqualTo(SettingKey.WALLET_HOLDING_LIMIT.defaultValue());
  }

  // 목적: 하드 상한과 같은 값은 그대로 돌려주는지 확인한다.
  @Test
  @DisplayName("하드 상한과 같은 값은 그대로 돌려준다")
  void getLong_valueAtHardMax() {
    settingRepository.save(
        OperatorSetting.create(SettingKey.WALLET_HOLDING_LIMIT, 2_000_000L, null, NOW));

    assertThat(settingService.getLong(SettingKey.WALLET_HOLDING_LIMIT)).isEqualTo(2_000_000L);
  }

  // 목적: 저장된 값이 하드 상한을 넘으면(상한이 나중에 낮아졌거나 DB 가 직접 수정된 경우) 상한으로 줄여서 돌려주는지 확인한다.
  @Test
  @DisplayName("저장된 값이 하드 상한을 넘으면 상한으로 줄여서 돌려준다")
  void getLong_cappedWhenStoredValueExceedsHardMax() {
    settingRepository.save(
        OperatorSetting.create(SettingKey.WALLET_HOLDING_LIMIT, 1_000_000L, null, NOW));
    entityManager.flush();
    entityManager.clear();
    jdbcTemplate.update(
        "update operator_setting set setting_value = 3000000 where setting_key = ?",
        SettingKey.WALLET_HOLDING_LIMIT.name());

    assertThat(settingService.getLong(SettingKey.WALLET_HOLDING_LIMIT)).isEqualTo(2_000_000L);
  }
}
