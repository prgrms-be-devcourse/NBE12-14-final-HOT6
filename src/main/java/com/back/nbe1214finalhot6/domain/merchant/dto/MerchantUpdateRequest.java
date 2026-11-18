package com.back.nbe1214finalhot6.domain.merchant.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 모든 필드는 선택이며, 값이 있으면 등록과 같은 규칙으로 검증한다. 값이 null 이면 기존 값을 유지한다.
public record MerchantUpdateRequest(
    @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") @Size(max = 100) String name,
    @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") @Size(max = 200) String address,
    Boolean cashbackEligible) {}
