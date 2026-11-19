package com.back.nbe1214finalhot6.domain.qr.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateRequest;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateResponse;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "QR", description = "결제 동적 QR 생성·조회 관련 API")
@RestController
@RequestMapping("/api/merchants/{merchantId}/qr")
@RequiredArgsConstructor
public class QrController {

  private final QrService qrService;

  @Operation(
      summary = "결제 동적 QR 생성",
      description = "결제 금액이 담긴 동적 QR 을 생성한다. 응답의 token 을 QR 로 그려서 보여 주며, 생성 시각 기준 3분 뒤에 만료된다")
  @PostMapping("/dynamic")
  public ResponseEntity<ApiResponse<QrCreateResponse>> createDynamic(
      @PathVariable Long merchantId, @Valid @RequestBody QrCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(qrService.createDynamic(merchantId, request)));
  }
}
