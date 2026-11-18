package com.back.nbe1214finalhot6.domain.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**TODO: ownerId 는 인증 머지 전까지 임시로 요청에서 받는다.
 * 머지 후에는 이 필드를 지우고 로그인한 회원의users.id 를 사용한다.
 * TODO: revenueProof 는 연매출 30억 미만 증빙 서류를 첨부했다고 가정하는 값이다.
 * 실제 파일 업로드와 검증은 하지 않고, 값이 있으면 적립 대상으로 자동 승인한다. */
public record MerchantRegisterRequest(
    @NotNull Long ownerId,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 200) String address,
    @Size(max = 255) String revenueProof) {

  public boolean resolveCashbackEligible() {
    return revenueProof != null && !revenueProof.isBlank();
  }
}
