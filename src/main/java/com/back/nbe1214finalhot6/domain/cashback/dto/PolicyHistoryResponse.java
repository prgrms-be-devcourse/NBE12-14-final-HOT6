package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicyHistory;
import com.back.nbe1214finalhot6.domain.cashback.entity.PolicyBasis;
import com.back.nbe1214finalhot6.domain.cashback.entity.PolicyHistoryAction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 정책(요율 구간) 변경 이력 한 건.
 *
 * @param historyId 이력 ID
 * @param policyId 변경된 정책(요율 구간) ID
 * @param action 변경 종류. {@code CREATE} 는 구간을 만든 것, {@code UPDATE} 는 기존 구간의 값(종료 시각 등)을 바꾼 것
 * @param changedBy 변경한 운영자의 회원 ID. 기록하기 전의 이력은 null
 * @param changedAt 변경 시각
 * @param beforeRate 변경 전 요율. {@code CREATE} 면 null
 * @param beforeStartsAt 변경 전 적용 시작. {@code CREATE} 면 null
 * @param beforeEndsAt 변경 전 적용 종료. {@code CREATE} 이거나 무기한이었으면 null
 * @param afterRate 변경 후 요율
 * @param afterStartsAt 변경 후 적용 시작
 * @param afterEndsAt 변경 후 적용 종료. 무기한이면 null
 * @param reason 인하 사유. 인하로 만든 구간의 이력에만 있다
 * @param basis 인하한 시점의 예산과 예측. 인하로 만든 구간의 이력에만 있다
 */
public record PolicyHistoryResponse(
    Long historyId,
    Long policyId,
    PolicyHistoryAction action,
    Long changedBy,
    LocalDateTime changedAt,
    BigDecimal beforeRate,
    LocalDateTime beforeStartsAt,
    LocalDateTime beforeEndsAt,
    BigDecimal afterRate,
    LocalDateTime afterStartsAt,
    LocalDateTime afterEndsAt,
    String reason,
    PolicyBasis basis) {

  public static PolicyHistoryResponse from(CashbackPolicyHistory history) {
    return new PolicyHistoryResponse(
        history.getId(),
        history.getPolicyId(),
        history.getAction(),
        history.getChangedBy(),
        history.getChangedAt(),
        history.getBeforeRate(),
        history.getBeforeStartsAt(),
        history.getBeforeEndsAt(),
        history.getAfterRate(),
        history.getAfterStartsAt(),
        history.getAfterEndsAt(),
        history.getReason(),
        history.getBasis());
  }
}
