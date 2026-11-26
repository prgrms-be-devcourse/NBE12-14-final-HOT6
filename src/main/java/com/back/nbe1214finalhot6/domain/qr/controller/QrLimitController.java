package com.back.nbe1214finalhot6.domain.qr.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.qr.dto.QrLimitResponse;
import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "QR", description = "결제 동적 QR 생성·조회 관련 API")
@RestController
@RequestMapping("/api/qr/limit")
public class QrLimitController {

  @Operation(
      summary = "결제 한도 조회",
      description = "QR 한 건으로 결제할 수 있는 최대 금액을 돌려준다. 점주 QR 금액 입력과 이용자 결제 화면이 한도 초과 안내에 쓴다")
  @GetMapping
  public ApiResponse<QrLimitResponse> getLimit() {
    return ApiResponse.ok(new QrLimitResponse(QrCode.MAX_AMOUNT));
  }
}
