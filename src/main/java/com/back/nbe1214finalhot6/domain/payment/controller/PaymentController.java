package com.back.nbe1214finalhot6.domain.payment.controller;

import com.back.nbe1214finalhot6.common.idempotency.IdempotencyKeys;
import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentCancelService;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentFacade;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "결제", description = "QR 결제 승인, 검증, 조회 및 취소 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentFacade paymentFacade;
  private final PaymentQueryService paymentQueryService;
  private final PaymentCancelService paymentCancelService;
  private final OwnedResources ownedResources; //인증인가

  @Operation(summary = "QR 결제", description = "멱등키 헤더로 중복 결제를 방지하며 QR 결제를 요청한다")
  @PostMapping
  public ApiResponse<PaymentResponse> pay(
          @RequestHeader("Idempotency-Key") String keyHeader,
          @RequestBody @Valid PaymentRequest request,
          @AuthenticationPrincipal AuthUser user) {
    String key = IdempotencyKeys.validate(keyHeader);
    Long walletId = ownedResources.walletIdOf(user);
    PaymentRequest trusted =
            new PaymentRequest(walletId, request.qrToken(), request.cashbackUsed());
    return ApiResponse.ok(paymentFacade.pay(key, trusted));
  }
  @Operation(
      summary = "응답 못 받은 QR 결제 재조회",
      description =
          "응답을 못 받은 결제를 같은 멱등키로 다시 조회한다. "
              + "결제를 새로 처리하지 않고 저장된 결과만 반환한다. "
              + "상태는 COMPLETED(완료) / FAILED(failureCode 참고) / PROCESSING(처리 중, 잠시 후 다시 조회) / CANCELED. "
              + "404(PAY-003)는 서버가 받은 적 없는 키라는 뜻이므로 같은 키로 다시 결제해도 된다")
  @GetMapping("/by-key/{key}")
  public ApiResponse<PaymentResponse> requery(
      @PathVariable String key,
      @AuthenticationPrincipal AuthUser user) {
    String validKey = IdempotencyKeys.validate(key);
    Long walletId = ownedResources.walletIdOf(user);
    return ApiResponse.ok(paymentQueryService.findByKey(walletId, validKey));
  }

  @Operation(summary = "결제 취소",
             description = "가맹점주가 결제 완료된 결제 1건을 전액 취소한다. 이미 취소된 결제는 기존 결과를 그대로 반환한다. "
                 + "COMPLETED 가 아닌 결제는 409(PAY-006), 다른 가맹점의 결제는 403(PAY-007)")
  @PostMapping("/{id}/cancel")
  public ApiResponse<PaymentResponse> cancel(
      @PathVariable Long id,
      @AuthenticationPrincipal AuthUser user) {
    Long merchantId = ownedResources.merchantIdOf(user);
    return ApiResponse.ok(paymentCancelService.cancel(merchantId, id));
  }
}
