package com.back.nbe1214finalhot6.domain.wallet.service;

import com.back.nbe1214finalhot6.domain.wallet.entity.Charge;
import java.time.LocalDateTime;

/** 충전 결과. 같은 멱등키로 다시 요청하면 처음 충전의 결과를 그대로 돌려줌. */
public record ChargeResult(Long chargeId, long amount, LocalDateTime createdAt) {

  public static ChargeResult from(Charge charge) {
    return new ChargeResult(charge.getId(), charge.getAmount(), charge.getCreatedAt());
  }
}
