package com.back.nbe1214finalhot6.domain.payment.controller;

import com.back.nbe1214finalhot6.common.idempotency.IdempotencyKeys;
import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentFacade;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "결제", description = "QR 결제 승인, 검증, 조회 및 취소 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentFacade paymentFacade;
  private final PaymentQueryService paymentQueryService;

  @Operation(summary = "QR 결제", description = "멱등키 헤더로 중복 결제를 방지하며 QR 결제를 요청한다")
  @PostMapping
  public ApiResponse<PaymentResponse> pay(@RequestHeader("Idempotency-Key") String keyHeader,
                                          @RequestBody @Valid PaymentRequest request) {
    // 멱등키 정합성 체크
    String key = IdempotencyKeys.validate(keyHeader);
    PaymentResponse paymentResponse = paymentFacade.pay(key,request);

    return ApiResponse.ok(paymentResponse);
  }

  @Operation(
      summary = "응답 못 받은 QR 결제 재조회",
      description =
          "응답을 못 받은 결제를 같은 멱등키로 다시 조회한다. "
              + "결제를 새로 처리하지 않고 저장된 결과만 반환한다. "
              + "상태는 COMPLETED(완료) / FAILED(failureCode 참고) / PROCESSING(처리 중, 잠시 후 다시 조회) / CANCELED. "
              + "404(COM-009)는 서버가 받은 적 없는 키라는 뜻이므로 같은 키로 다시 결제해도 된다")
  @GetMapping("/by-key/{key}")
  public ApiResponse<PaymentResponse> requery(
      @PathVariable String key,
      @RequestParam Long walletId) { // TODO: JWT 연동 후 토큰에서 추출
    String validKey = IdempotencyKeys.validate(key);

    return ApiResponse.ok(paymentQueryService.findByKey(walletId, validKey));
  }
}
