package com.back.nbe1214finalhot6.setting;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.setting.exception.SettingErrorCode;

/**
 * 운영자가 바꿀 수 있는 설정 키. 키마다 기본값, 최솟값, 하드 상한을 가진다.
 *
 * <p>하드 상한은 설정값이 아니라 코드 상수다. 운영자는 상한 이하(상한과 같은 값 포함)로만 설정할 수 있고, 상한이 없는 키는 {@link Long#MAX_VALUE} 로
 * 둔다.
 */
public enum SettingKey {
  /** 이용자 한 명이 한 달에 받을 수 있는 캐시백 적립 한도(원). 상한은 팀 회의 후 정한다. */
  CASHBACK_USER_MONTHLY_LIMIT("이용자 월 적립 한도(원)", 50_000L, 1L, Long.MAX_VALUE),

  /**
   * 지갑 보유 한도(원). 충전 시 {@code 데브머니 잔액 + 충전액 ≤ 한도}. 상한은 기명식 선불전자지급수단의 저장 한도 200만원
   * (전자금융거래법 제23조, 시행령 제13조)이다.
   */
  WALLET_HOLDING_LIMIT("지갑 보유 한도(원)", 2_000_000L, 1L, 2_000_000L),

  /** 지갑 월 충전 한도(원). 충전 시 {@code 이번 달 누적 충전액 + 충전액 ≤ 한도}. 상한은 정책 값이다. */
  WALLET_MONTHLY_CHARGE_LIMIT("지갑 월 충전 한도(원)", 1_000_000L, 1L, 1_000_000L);

  private final String description;
  private final long defaultValue;
  private final long minValue;
  private final long hardMax;

  SettingKey(String description, long defaultValue, long minValue, long hardMax) {
    this.description = description;
    this.defaultValue = defaultValue;
    this.minValue = minValue;
    this.hardMax = hardMax;
  }

  public String description() {
    return description;
  }

  /** 운영자가 값을 바꾼 적이 없을 때 쓰는 값. */
  public long defaultValue() {
    return defaultValue;
  }

  public long minValue() {
    return minValue;
  }

  /** 하드 상한. 상한이 없으면 {@link Long#MAX_VALUE} 이다. */
  public long hardMax() {
    return hardMax;
  }

  public boolean hasHardMax() {
    return hardMax != Long.MAX_VALUE;
  }

  /**
   * 값이 허용 범위(최솟값 이상, 하드 상한 이하)인지 검사한다. 상한과 같은 값은 허용한다.
   *
   * @throws BusinessException 범위를 벗어났을 때(SET-001)
   */
  public void validate(long value) {
    if (value < minValue || value > hardMax) {
      String range =
          hasHardMax()
              ? minValue + " 이상 " + hardMax + " 이하"
              : minValue + " 이상";
      throw new BusinessException(
          SettingErrorCode.INVALID_SETTING, description + "은(는) " + range + "이어야 합니다");
    }
  }

  /** 값이 하드 상한을 넘으면 상한으로 줄인다. 저장된 값이 나중에 낮아진 상한을 넘는 경우에 대비한다. */
  public long capped(long value) {
    return Math.min(value, hardMax);
  }
}
