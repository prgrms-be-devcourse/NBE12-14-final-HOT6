package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PolicyResponse(
    Long policyId,
    BigDecimal rate,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    LocalDateTime createdAt) {

  public static PolicyResponse from(CashbackPolicy policy) {
    return new PolicyResponse(
        policy.getId(),
        policy.getRate(),
        policy.getStartsAt(),
        policy.getEndsAt(),
        policy.getCreatedAt());
  }
}
