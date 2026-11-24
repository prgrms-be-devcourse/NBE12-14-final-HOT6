package com.back.nbe1214finalhot6.domain.wallet.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.wallet.dto.WalletTransactionResponse;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "지갑", description = "내 지갑 거래 내역 조회 API")
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletTransactionController {

  private final OwnedResources ownedResources;
  private final WalletTransactionService walletTransactionService;

  @Operation(
      summary = "내 지갑 거래 내역 조회",
      description =
          "로그인한 회원의 지갑 거래 내역을 최신순으로 돌려준다. 충전, 환불, 결제, 결제 취소, 캐시백 적립·회수가 포함되며"
              + " 금액은 입금이 +, 차감이 -이다. balanceAfter 는 그 거래 직후의 잔액이고 cashback 이 true 면 캐시백 잔액이다."
              + " 적립 상태(적립 예정·지급 완료)는 캐시백 적립 내역 API 와 화면에서 합친다")
  @GetMapping("/transactions")
  public ApiResponse<PageResponse<WalletTransactionResponse>> transactions(
      @AuthenticationPrincipal AuthUser user,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) int size) {
    Long walletId = ownedResources.walletIdOf(user);
    return ApiResponse.ok(walletTransactionService.getTransactions(walletId, page, size));
  }
}
