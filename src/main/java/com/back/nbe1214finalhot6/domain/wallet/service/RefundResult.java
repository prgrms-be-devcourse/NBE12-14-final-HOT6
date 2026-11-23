package com.back.nbe1214finalhot6.domain.wallet.service;

import com.back.nbe1214finalhot6.domain.wallet.entity.Refund;
import java.time.LocalDateTime;

/** 환불 결과. 같은 멱등키로 다시 요청하면 처음 환불의 결과를 그대로 돌려줌. */
public record RefundResult(Long refundId, long amount, LocalDateTime createdAt) {

  public static RefundResult from(Refund refund) {
    return new RefundResult(refund.getId(), refund.getAmount(), refund.getCreatedAt());
  }
}
