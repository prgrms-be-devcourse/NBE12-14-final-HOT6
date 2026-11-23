package com.back.nbe1214finalhot6.domain.merchant.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.wallet.dto.MerchantAccountResponse;
import com.back.nbe1214finalhot6.domain.wallet.service.MerchantWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "가맹점", description = "가맹점 등록·수정·검색 관련 API")
@RestController
@RequestMapping("/api/merchants/me/account")
@RequiredArgsConstructor
public class MerchantAccountController {

  private final MerchantWalletService walletService;
  private final OwnedResources ownedResources;

  @Operation(
      summary = "정산 계좌 잔액 조회",
      description =
          "로그인한 점주의 가맹점 정산 계좌 잔액을 조회한다(가맹점 번호는 토큰의 회원으로 찾는다). "
              + "월 정산 지급액이 이 계좌로 들어와 쌓인다")
  @GetMapping
  public ApiResponse<MerchantAccountResponse> getAccount(@AuthenticationPrincipal AuthUser user) {
    return ApiResponse.ok(walletService.getAccount(ownedResources.merchantIdOf(user)));
  }
}
