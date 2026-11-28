package com.back.nbe1214finalhot6.domain.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 연매출 30억 미만 증빙 서류를 나중에 제출한다.
 * TODO: 등록과 같이 실제 파일 업로드와 검증은 하지 않고, 첨부했다고 가정하는 값(파일 이름)이다.
 */
public record MerchantRevenueProofRequest(@NotBlank @Size(max = 255) String revenueProof) {}
