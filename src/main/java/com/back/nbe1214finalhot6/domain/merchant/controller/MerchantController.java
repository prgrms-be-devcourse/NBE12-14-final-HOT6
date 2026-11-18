package com.back.nbe1214finalhot6.domain.merchant.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantUpdateRequest;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "가맹점", description = "가맹점 등록·수정·검색 관련 API")
@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
public class MerchantController {

  private final MerchantService merchantService;

  @Operation(
      summary = "가맹점 등록",
      description = "가맹점을 등록한다. 연매출 30억 미만 증빙 서류(revenueProof)를 첨부하면 적립 대상으로 자동 승인되고, 첨부하지 않으면 적립 대상에서 제외된다")
  @PostMapping
  public ResponseEntity<ApiResponse<MerchantRegisterResponse>> register(
      @Valid @RequestBody MerchantRegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(merchantService.register(request)));
  }

  @Operation(
      summary = "가맹점 수정",
      description = "이름, 주소, 적립 대상 여부 중 요청에 포함된 항목만 수정한다. 포함되지 않은 항목은 기존 값을 유지한다")
  @PatchMapping("/{merchantId}")
  public ApiResponse<MerchantResponse> update(
      @PathVariable Long merchantId, @Valid @RequestBody MerchantUpdateRequest request) {
    return ApiResponse.ok(merchantService.update(merchantId, request));
  }

  @Operation(
      summary = "가맹점 이름 검색",
      description = "이름에 키워드가 포함된 가맹점을 이름순으로 조회한다. size 는 기본 20, 최대 50 이다")
  @GetMapping
  public ApiResponse<PageResponse<MerchantResponse>> search(
      @RequestParam @NotBlank String name,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size) {
    return ApiResponse.ok(merchantService.search(name, page, size));
  }
}
