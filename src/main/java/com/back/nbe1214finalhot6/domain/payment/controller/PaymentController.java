package com.back.nbe1214finalhot6.domain.payment.controller;

import com.back.nbe1214finalhot6.common.idempotency.IdempotencyKeys;
import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;

import com.back.nbe1214finalhot6.domain.payment.service.PaymentFacade;
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

  @Operation(summary = "변동 QR 결제",
             description = "멱등키 헤더로 중복 결제를 방지하며 QR 결제를 요청한다")
  @PostMapping
  public ApiResponse<PaymentResponse> pay(@RequestHeader("Idempotency-Key") String keyHeader,
                                          @RequestBody @Valid PaymentRequest request) {
    // 멱등키 정합성 체크
    String key = IdempotencyKeys.validate(keyHeader);
    PaymentResponse paymentResponse = paymentFacade.pay(key,request);

    return ApiResponse.ok(paymentResponse);
  }
}
