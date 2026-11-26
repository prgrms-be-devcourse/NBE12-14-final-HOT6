package com.back.nbe1214finalhot6.domain.merchant.dto;

import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import java.time.LocalDateTime;

/** 해지 신청 결과. 신청 직후 가맹점 상태는 CLOSING 이다. */
public record ClosureResponse(MerchantStatus status, LocalDateTime requestedAt) {}
