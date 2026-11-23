package com.back.nbe1214finalhot6.domain.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 등록하는 회원은 요청이 아니라 로그인한 회원(토큰)으로 정한다.
 * TODO: revenueProof 는 연매출 30억 미만 증빙 서류를 첨부했다고 가정하는 값이다.
 * 실제 파일 업로드와 검증은 하지 않고, 값이 있으면 적립 대상으로 자동 승인한다. */
public record MerchantRegisterRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 200) String address,
    @Size(max = 255) String revenueProof) {

  public boolean resolveCashbackEligible() {
    return revenueProof != null && !revenueProof.isBlank();
  }
}
