package com.back.nbe1214finalhot6.domain.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**TODO: ownerId 는 인증 머지 전까지 임시로 요청에서 받는다.
 * 머지 후에는 이 필드를 지우고 로그인한 회원의users.id 를 사용한다. */
public record MerchantRegisterRequest(
    @NotNull Long ownerId,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 200) String address,
    Boolean cashbackEligible) {

  public boolean resolveCashbackEligible() {
    return cashbackEligible == null || cashbackEligible;
  }
}
