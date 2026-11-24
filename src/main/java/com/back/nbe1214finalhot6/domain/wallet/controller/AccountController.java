package com.back.nbe1214finalhot6.domain.wallet.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.wallet.dto.AccountResponse;
import com.back.nbe1214finalhot6.domain.wallet.dto.AccountTransactionResponse;
import com.back.nbe1214finalhot6.domain.wallet.service.AccountQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 모의계좌 조회 API. 계좌는 요청으로 받지 않고 로그인한 회원(토큰)으로 찾는다. */
@Tag(name = "모의계좌", description = "내 모의계좌 잔액과 거래 내역 조회 API")
@RestController
@RequestMapping("/api/wallet/account")
@RequiredArgsConstructor
public class AccountController {

  private final AccountQueryService accountQueryService;

  @Operation(
      summary = "내 모의계좌 잔액 조회",
      description = "로그인한 회원의 모의계좌 잔액을 돌려준다. 계좌가 없으면 404(WAL-008)")
  @GetMapping
  public ApiResponse<AccountResponse> account(@AuthenticationPrincipal AuthUser user) {
    return ApiResponse.ok(accountQueryService.getAccount(user.userId()));
  }

  @Operation(
      summary = "내 모의계좌 거래 내역 조회",
      description =
          "로그인한 회원의 모의계좌 거래 내역을 최신순으로 돌려준다. 가입 보너스, 충전, 환불이 포함되며 금액은 입금이 +, 차감이 -이다"
              + "(충전은 계좌에서 빠지므로 -, 환불은 계좌로 돌아오므로 +). balanceAfter 는 그 거래 직후의 계좌 잔액이다."
              + " 지갑 쪽 거래 내역은 /api/wallet/transactions 에서 본다")
  @GetMapping("/transactions")
  public ApiResponse<PageResponse<AccountTransactionResponse>> transactions(
      @AuthenticationPrincipal AuthUser user,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) int size) {
    return ApiResponse.ok(accountQueryService.getTransactions(user.userId(), page, size));
  }
}
