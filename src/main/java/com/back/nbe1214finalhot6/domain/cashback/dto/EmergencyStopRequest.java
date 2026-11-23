package com.back.nbe1214finalhot6.domain.cashback.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 긴급 중단 요청. 즉시 적립을 멈추는 되돌리기 어려운 작업이라 실수로 호출하지 않도록 확인 값을 요구한다.
 *
 * @param confirm 반드시 {@code true}. 없거나 {@code false} 면 400
 */
public record EmergencyStopRequest(@NotNull @AssertTrue Boolean confirm) {}
