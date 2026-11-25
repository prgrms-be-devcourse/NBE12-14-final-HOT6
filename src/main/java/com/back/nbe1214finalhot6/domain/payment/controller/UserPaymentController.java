package com.back.nbe1214finalhot6.domain.payment.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.payment.dto.UserPaymentsResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.service.UserPaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제", description = "QR 결제 승인, 검증, 조회 및 취소 관련 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class UserPaymentController {

  private final UserPaymentQueryService userPaymentQueryService;
  private final OwnedResources ownedResources;

  @Operation(
      summary = "내 결제 내역 조회",
      description =
          "로그인한 이용자의 결제 내역을 최신순으로 조회한다(지갑은 토큰의 회원으로 찾는다). "
              + "status 는 COMPLETED(완료) 또는 CANCELED(취소)이고 생략하면 둘 다 보여 준다. "
              + "처리 중·실패한 결제는 포함하지 않는다. 한 페이지는 기본 10건, 최대 100건이다. "
              + "각 건에는 가맹점명, 데브머니·캐시백 사용분, 적립 캐시백, 완료·취소 시각이 담긴다")
  @GetMapping
  public ApiResponse<UserPaymentsResponse> get(
      @AuthenticationPrincipal AuthUser user,
      @RequestParam(required = false) PaymentStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) int size) {
    return ApiResponse.ok(
        userPaymentQueryService.get(ownedResources.walletIdOf(user), status, page, size));
  }
}
