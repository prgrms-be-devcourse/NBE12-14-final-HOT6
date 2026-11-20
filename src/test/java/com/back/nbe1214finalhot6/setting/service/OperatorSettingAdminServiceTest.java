package com.back.nbe1214finalhot6.setting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.dto.SettingResponse;
import com.back.nbe1214finalhot6.setting.dto.SettingSource;
import com.back.nbe1214finalhot6.setting.entity.OperatorSettingHistory;
import com.back.nbe1214finalhot6.setting.exception.SettingErrorCode;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingHistoryRepository;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 운영자 설정 조회·변경·되돌리기. 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class OperatorSettingAdminServiceTest {

  private static final String CHARGE = SettingKey.WALLET_MONTHLY_CHARGE_LIMIT.name();
  private static final String HOLDING = SettingKey.WALLET_HOLDING_LIMIT.name();
  private static final String CASHBACK = SettingKey.CASHBACK_USER_MONTHLY_LIMIT.name();

  @Autowired private OperatorSettingAdminService adminService;
  @Autowired private OperatorSettingService settingService;
  @Autowired private OperatorSettingRepository settingRepository;
  @Autowired private OperatorSettingHistoryRepository historyRepository;

  private List<OperatorSettingHistory> historyOf(SettingKey key) {
    return historyRepository.findAllBySettingKeyOrderByIdAsc(key);
  }

  private void assertInvalid(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SettingErrorCode.INVALID_SETTING);
  }

  // 목적: 바꾼 적이 없으면 모든 키가 기본값(출처 DEFAULT)으로 나오고, 최솟값과 하드 상한(없으면 null)이 함께 나오는지 확인한다.
  @Test
  @DisplayName("조회하면 모든 키가 기본값과 범위 정보와 함께 나온다")
  void list_defaults() {
    List<SettingResponse> all = adminService.list();

    assertThat(all).extracting(SettingResponse::key).containsExactly(SettingKey.values());
    assertThat(all)
        .allSatisfy(
            setting -> {
              assertThat(setting.source()).isEqualTo(SettingSource.DEFAULT);
              assertThat(setting.value()).isEqualTo(setting.defaultValue());
              assertThat(setting.updatedAt()).isNull();
            });
    SettingResponse holding =
        all.stream()
            .filter(s -> s.key() == SettingKey.WALLET_HOLDING_LIMIT)
            .findFirst()
            .orElseThrow();
    assertThat(holding.hardMax()).isEqualTo(2_000_000L);
    SettingResponse cashback =
        all.stream()
            .filter(s -> s.key() == SettingKey.CASHBACK_USER_MONTHLY_LIMIT)
            .findFirst()
            .orElseThrow();
    assertThat(cashback.hardMax()).isNull();
  }

  // 목적: 값을 설정하면 출처가 OPERATOR 로 바뀌고 읽기 서비스(getLong)에 바로 반영되며 변경 이력이 한 건 남는지 확인한다.
  @Test
  @DisplayName("값을 설정하면 이후 읽기에 반영되고 이력이 남는다")
  void put_createsAndRecordsHistory() {
    SettingResponse response = adminService.put(CHARGE, 700_000L);

    assertThat(response.value()).isEqualTo(700_000L);
    assertThat(response.source()).isEqualTo(SettingSource.OPERATOR);
    assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 0));
    assertThat(response.updatedBy()).isNull();
    assertThat(settingService.getLong(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT)).isEqualTo(700_000L);

    List<OperatorSettingHistory> history = historyOf(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT);
    assertThat(history).hasSize(1);
    assertThat(history.getFirst().getBeforeValue()).isEqualTo(1_000_000L); // 기본값에서 바꿈
    assertThat(history.getFirst().getAfterValue()).isEqualTo(700_000L);
    assertThat(history.getFirst().getChangedBy()).isNull();
  }

  // 목적: 이미 설정한 값을 다시 바꾸면 변경 전 값이 직전 설정값으로 이력에 이어지는지 확인한다.
  @Test
  @DisplayName("다시 바꾸면 이력의 변경 전 값이 직전 값으로 이어진다")
  void put_changeChainsHistory() {
    adminService.put(CHARGE, 700_000L);
    adminService.put(CHARGE, 500_000L);

    List<OperatorSettingHistory> history = historyOf(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT);
    assertThat(history).extracting(OperatorSettingHistory::getBeforeValue)
        .containsExactly(1_000_000L, 700_000L);
    assertThat(history).extracting(OperatorSettingHistory::getAfterValue)
        .containsExactly(700_000L, 500_000L);
    assertThat(settingService.getLong(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT)).isEqualTo(500_000L);
  }

  // 목적: 지금 적용되는 값과 같은 값으로 설정하면(행이 있든 없든) 행도 이력도 만들지 않고 현재 상태를 돌려주는지 확인한다.
  @Test
  @DisplayName("현재 값과 같은 값으로 설정하면 아무것도 바뀌지 않는다")
  void put_sameValueIsNoOp() {
    // 행이 없고 기본값과 같은 값: 행을 만들지 않고 DEFAULT 로 남는다
    SettingResponse unchanged = adminService.put(CHARGE, 1_000_000L);
    assertThat(unchanged.source()).isEqualTo(SettingSource.DEFAULT);
    assertThat(settingRepository.count()).isZero();
    assertThat(historyRepository.count()).isZero();

    // 행이 있고 같은 값: 이력이 늘지 않는다
    adminService.put(CHARGE, 600_000L);
    adminService.put(CHARGE, 600_000L);
    assertThat(historyOf(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT)).hasSize(1);
  }

  // 목적: 하드 상한과 같은 값은 설정할 수 있고, 상한을 넘거나 최솟값 미만이거나 없는 키는 SET-001 로 거부되며 아무것도 저장되지 않는지 확인한다.
  @Test
  @DisplayName("상한과 같은 값은 허용하고 범위 밖 값과 없는 키는 거부한다")
  void put_validation() {
    adminService.put(HOLDING, 1_000_000L);
    assertThat(adminService.put(HOLDING, 2_000_000L).value()).isEqualTo(2_000_000L); // 상한과 같음

    assertInvalid(() -> adminService.put(HOLDING, 2_000_001L));
    assertInvalid(() -> adminService.put(CHARGE, 1_000_001L));
    assertInvalid(() -> adminService.put(CHARGE, 0L));
    assertInvalid(() -> adminService.put(CASHBACK, -5L));
    assertInvalid(() -> adminService.put("NO_SUCH_KEY", 1_000L));

    assertThat(settingService.getLong(SettingKey.WALLET_HOLDING_LIMIT)).isEqualTo(2_000_000L);
    assertThat(historyRepository.count()).isEqualTo(2);
  }

  // 목적: 상한이 없는 키는 큰 값도 설정할 수 있는지 확인한다.
  @Test
  @DisplayName("상한이 없는 키는 큰 값도 설정할 수 있다")
  void put_withoutHardMax() {
    assertThat(adminService.put(CASHBACK, 10_000_000L).value()).isEqualTo(10_000_000L);
  }

  // 목적: 되돌리면 행이 지워져 기본값(DEFAULT)으로 돌아가고, 변경 후 값이 null 인 이력이 남는지 확인한다.
  @Test
  @DisplayName("되돌리면 기본값으로 돌아가고 이력이 남는다")
  void reset_restoresDefault() {
    adminService.put(CHARGE, 400_000L);

    SettingResponse response = adminService.reset(CHARGE);

    assertThat(response.source()).isEqualTo(SettingSource.DEFAULT);
    assertThat(response.value()).isEqualTo(1_000_000L);
    assertThat(settingService.getLong(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT))
        .isEqualTo(1_000_000L);
    List<OperatorSettingHistory> history = historyOf(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT);
    assertThat(history).hasSize(2);
    assertThat(history.get(1).getBeforeValue()).isEqualTo(400_000L);
    assertThat(history.get(1).getAfterValue()).isNull();
  }

  // 목적: 바꾼 적이 없는 키를 되돌려도 이력이 남지 않고, 없는 키는 SET-001 로 거부되는지 확인한다.
  @Test
  @DisplayName("바꾼 적 없는 키를 되돌리면 아무것도 바뀌지 않는다")
  void reset_noOpAndUnknownKey() {
    SettingResponse response = adminService.reset(CHARGE);

    assertThat(response.source()).isEqualTo(SettingSource.DEFAULT);
    assertThat(historyRepository.count()).isZero();
    assertInvalid(() -> adminService.reset("NO_SUCH_KEY"));
  }

  // 목적: 되돌린 뒤 다시 설정하면 새로 설정한 것으로 이력이 이어지는지 확인한다.
  @Test
  @DisplayName("되돌린 뒤 다시 설정할 수 있다")
  void putAfterReset() {
    adminService.put(CHARGE, 400_000L);
    adminService.reset(CHARGE);

    assertThat(adminService.put(CHARGE, 300_000L).source()).isEqualTo(SettingSource.OPERATOR);
    assertThat(historyOf(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT)).hasSize(3);
  }
}
