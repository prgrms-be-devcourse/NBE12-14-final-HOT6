package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackPreviewRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackPreviewResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackService;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 미리보기(이용자)", description = "결제 전에 받을 수 있는 캐시백을 미리 계산하는 API. 지갑은 토큰의 회원으로 찾는다")
@RestController
@RequestMapping("/api/cashback")
@RequiredArgsConstructor
public class CashbackPreviewController {

  private final CashbackService cashbackService;
  private final MerchantService merchantService;
  private final OwnedResources ownedResources;

  @Operation(
      summary = "결제 전 캐시백 미리보기",
      description =
          "지금(서버 시각) 기준으로 이 결제에서 받을 수 있는 캐시백을 계산한다. 읽기만 하고 아무것도 확보하지 않는다."
              + " '예상' 값이라 결제가 확정될 때 달라질 수 있다. walletUsedAmount 는 데브머니로 결제할 금액이다")
  @GetMapping("/preview")
  public ApiResponse<CashbackPreviewResponse> preview(
      @AuthenticationPrincipal AuthUser user,
      @RequestParam Long merchantId,
      @RequestParam @Min(0) long walletUsedAmount) {
    Long walletId = ownedResources.walletIdOf(user);
    boolean eligible = merchantService.isCashbackEligible(merchantId);
    // 본인 가맹점 결제는 적립하지 않는다(#301). 결제 때와 같은 기준(지갑 주인과 점주의 전화번호)으로 미리 알려 준다
    boolean selfPayment = merchantService.isOwnedBySamePerson(merchantId, walletId);
    return ApiResponse.ok(
        cashbackService.preview(
            new CashbackPreviewRequest(walletId, eligible, walletUsedAmount, selfPayment)));
  }
}
