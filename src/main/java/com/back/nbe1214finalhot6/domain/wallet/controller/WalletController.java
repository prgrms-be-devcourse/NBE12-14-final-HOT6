package com.back.nbe1214finalhot6.domain.wallet.controller;

import com.back.nbe1214finalhot6.common.idempotency.IdempotencyKeys;
import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.wallet.dto.ChargeRequest;
import com.back.nbe1214finalhot6.domain.wallet.dto.RefundRequest;
import com.back.nbe1214finalhot6.domain.wallet.dto.WalletResponse;
import com.back.nbe1214finalhot6.domain.wallet.service.ChargeResult;
import com.back.nbe1214finalhot6.domain.wallet.service.ChargeService;
import com.back.nbe1214finalhot6.domain.wallet.service.RefundResult;
import com.back.nbe1214finalhot6.domain.wallet.service.RefundService;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 지갑 API(조회·충전·환불).
 *
 * <p>지갑은 요청으로 받지 않고 로그인한 회원(토큰)으로 찾는다. 남의 지갑을 가리킬 방법이 없다.
 */
@Tag(name = "지갑", description = "내 지갑 조회·충전·환불 API")
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

  private final WalletQueryService walletQueryService;
  private final ChargeService chargeService;
  private final RefundService refundService;
  private final OwnedResources ownedResources;

  @Operation(
      summary = "내 지갑 조회",
      description =
          "데브머니, 캐시백, 결제에 쓸 수 있는 캐시백(캐시백이 음수면 0), 보유·월 충전 한도, 이번 달 충전액, 남은 충전 가능액을 돌려준다. "
              + "적립 예정 금액은 적립 내역 API 와 함께 보여 준다")
  @GetMapping
  public ApiResponse<WalletResponse> getMyWallet(@AuthenticationPrincipal AuthUser user) {
    return ApiResponse.ok(walletQueryService.getMyWallet(user.userId()));
  }

  @Operation(
      summary = "충전",
      description =
          "모의계좌에서 내 지갑(데브머니)으로 충전한다. `Idempotency-Key`(UUID) 헤더가 필요하고, 같은 키로 다시 요청하면 "
              + "한 번만 반영하고 처음 결과를 그대로 돌려준다. 보유 한도(409 WAL-006), 월 충전 한도(409 WAL-007), "
              + "모의계좌 잔액 부족(409 WAL-009)이면 거절한다")
  @PostMapping("/charges")
  public ApiResponse<ChargeResult> charge(
      @RequestHeader("Idempotency-Key") String keyHeader,
      @RequestBody @Valid ChargeRequest request,
      @AuthenticationPrincipal AuthUser user) {
    String key = IdempotencyKeys.validate(keyHeader);
    return ApiResponse.ok(
        chargeService.charge(ownedResources.walletIdOf(user), request.amount(), key));
  }

  @Operation(
      summary = "환불",
      description =
          "내 지갑의 데브머니를 모의계좌로 환불한다(캐시백은 환불할 수 없다). `Idempotency-Key`(UUID) 헤더가 필요하고, "
              + "같은 키로 다시 요청하면 한 번만 반영하고 처음 결과를 그대로 돌려준다. 데브머니 잔액이 부족하면 409 WAL-004")
  @PostMapping("/refunds")
  public ApiResponse<RefundResult> refund(
      @RequestHeader("Idempotency-Key") String keyHeader,
      @RequestBody @Valid RefundRequest request,
      @AuthenticationPrincipal AuthUser user) {
    String key = IdempotencyKeys.validate(keyHeader);
    return ApiResponse.ok(
        refundService.refund(ownedResources.walletIdOf(user), request.amount(), key));
  }
}
