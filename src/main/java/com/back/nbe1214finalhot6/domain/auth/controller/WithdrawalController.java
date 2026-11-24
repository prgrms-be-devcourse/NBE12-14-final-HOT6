package com.back.nbe1214finalhot6.domain.auth.controller;

import com.back.nbe1214finalhot6.common.idempotency.IdempotencyKeys;
import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.dto.WithdrawalPreviewResponse;
import com.back.nbe1214finalhot6.domain.auth.dto.WithdrawalResult;
import com.back.nbe1214finalhot6.domain.auth.dto.WithdrawalSettleRequest;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.auth.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 탈퇴 API. 지갑은 요청으로 받지 않고 로그인한 회원(토큰)으로 찾는다.
 *
 * <p>2단계다. 먼저 미리보기로 환불 금액을 확인하고, 그 금액을 담아 실행한다.
 */
@Tag(name = "탈퇴", description = "회원 탈퇴 미리보기와 실행 API")
@RestController
@RequestMapping("/api/users/withdraw")
@RequiredArgsConstructor
public class WithdrawalController {

  private final WithdrawalService withdrawalService;
  private final OwnedResources ownedResources;

  @Operation(
      summary = "탈퇴 미리보기",
      description =
          "탈퇴하면 환불될 금액(남은 데브머니)과 소멸될 적립 예정 캐시백을 보여 준다. 아무것도 바꾸지 않는다. "
              + "점주·운영자는 409(AUTH-007), 이미 탈퇴했으면 409(WAL-002)")
  @PostMapping
  public ApiResponse<WithdrawalPreviewResponse> preview(@AuthenticationPrincipal AuthUser user) {
    return ApiResponse.ok(withdrawalService.preview(user.userId(), ownedResources.walletIdOf(user)));
  }

  @Operation(
      summary = "탈퇴 실행",
      description =
          "남은 데브머니를 모의계좌로 환불하고, 적립 예정 캐시백을 소멸시키고, 탈퇴 처리한다(한 트랜잭션). "
              + "`Idempotency-Key`(UUID) 헤더와 미리보기에서 확인한 환불 예정액이 필요하고, 그 사이 금액이 달라졌으면 "
              + "409(AUTH-008)로 거절하니 금액을 다시 확인해 다시 요청한다. 음수 캐시백은 정산하지 않고 감수한다. "
              + "점주·운영자는 409(AUTH-007), 이미 탈퇴했으면 409(WAL-002)")
  @PostMapping("/settle")
  public ApiResponse<WithdrawalResult> settle(
      @RequestHeader("Idempotency-Key") String keyHeader,
      @RequestBody @Valid WithdrawalSettleRequest request,
      @AuthenticationPrincipal AuthUser user) {
    String key = IdempotencyKeys.validate(keyHeader);
    return ApiResponse.ok(
        withdrawalService.settle(
            user.userId(), ownedResources.walletIdOf(user), request.expectedRefundAmount(), key));
  }
}
