package com.back.nbe1214finalhot6.domain.merchant.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "가맹점", description = "가맹점 등록 관련 API")
@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
public class MerchantController {

  private final MerchantService merchantService;

  @Operation(
      summary = "가맹점 등록",
      description = "가맹점을 등록한다. 적립 대상 여부(cashbackEligible)를 생략하면 true 로 등록된다")
  @PostMapping
  public ResponseEntity<ApiResponse<MerchantRegisterResponse>> register(
      @Valid @RequestBody MerchantRegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(merchantService.register(request)));
  }
}
