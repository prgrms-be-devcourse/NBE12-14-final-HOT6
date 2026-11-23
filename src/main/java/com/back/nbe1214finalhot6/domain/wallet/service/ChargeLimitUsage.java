package com.back.nbe1214finalhot6.domain.wallet.service;

/**
 * 충전 한도 현황. 충전 검사(ChargeLimitService)와 지갑 조회 화면이 같은 값을 쓰도록 한 묶음으로 둠.
 *
 * @param holdingLimit 보유 한도(운영자 설정)
 * @param monthlyChargeLimit 월 충전 한도(운영자 설정)
 * @param chargedThisMonth 이번 달(한국 시간 달력월) 누적 충전액. 환불해도 줄지 않음
 */
public record ChargeLimitUsage(long holdingLimit, long monthlyChargeLimit, long chargedThisMonth) {

  /**
   * 남은 충전 가능액. 보유 한도와 월 충전 한도 중 더 빡빡한 쪽이며, 한도를 이미 넘은 상태면 음수가 아니라 0. 충전 검사(assertChargeable)가 허용하는
   * 최대 충전액과 같음. 모의계좌 잔액은 반영하지 않음.
   *
   * @param devMoney 현재 데브머니 잔액(보유 한도 계산에 쓰고, 캐시백 잔액은 포함하지 않음)
   */
  public long chargeableAmount(long devMoney) {
    return Math.max(0, Math.min(holdingLimit - devMoney, monthlyChargeLimit - chargedThisMonth));
  }
}
