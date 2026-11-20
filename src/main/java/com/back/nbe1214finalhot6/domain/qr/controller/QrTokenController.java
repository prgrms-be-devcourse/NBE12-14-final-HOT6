package com.back.nbe1214finalhot6.domain.qr.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.qr.dto.QrScanResponse;
import com.back.nbe1214finalhot6.domain.qr.dto.QrStatusResponse;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "QR", description = "결제 동적 QR 생성·조회 관련 API")
@RestController
@RequestMapping("/api/qr/{token}")
@RequiredArgsConstructor
public class QrTokenController {

  private final QrService qrService;

  @Operation(
      summary = "QR 스캔 조회",
      description = "손님이 QR 을 스캔했을 때 결제 전에 가맹점, 금액, 적립 대상 여부를 보여 준다. 결제 요청에는 스캔한 token 만 사용한다. 만료된 QR 은 410, 이미 사용된 QR 은 409 를 반환한다")
  @GetMapping
  public ApiResponse<QrScanResponse> scan(@PathVariable String token) {
    return ApiResponse.ok(qrService.scan(token));
  }

  @Operation(
      summary = "QR 상태 조회",
      description = "QR 의 현재 상태(WAITING, USED, EXPIRED)와 만료 시각을 반환한다. 사장님 화면이 결제 진행 여부를 확인하려고 주기적으로 호출한다")
  @GetMapping("/status")
  public ApiResponse<QrStatusResponse> getStatus(@PathVariable String token) {
    return ApiResponse.ok(qrService.getStatus(token));
  }
}
