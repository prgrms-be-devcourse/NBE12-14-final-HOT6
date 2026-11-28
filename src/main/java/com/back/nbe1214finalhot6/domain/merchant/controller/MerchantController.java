package com.back.nbe1214finalhot6.domain.merchant.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRevenueProofRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantUpdateRequest;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantRegistrationFacade;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantService;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantUpdateFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "가맹점", description = "가맹점 등록·수정·검색·해지 관련 API")
@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
public class MerchantController {

  private final MerchantService merchantService;
  private final MerchantRegistrationFacade registrationFacade;
  private final MerchantUpdateFacade updateFacade;
  private final OwnedResources ownedResources;

  @Operation(
      summary = "가맹점 등록",
      description =
          "로그인한 점주의 가맹점을 등록한다. 연매출 30억 미만 증빙 서류(revenueProof)를 첨부하면 적립 대상으로 자동 승인되고, 첨부하지 않으면 적립 대상에서 제외된다. "
              + "주소는 서버가 좌표로 바꿔 저장하며(좌표는 요청으로 받지 않는다), 주소를 찾지 못하면 400(MER-006)이다. 외부 서비스가 실패하거나 설정이 비어 있으면 위치 없이 등록한다. "
              + "점주 한 명당 가맹점은 하나만 등록할 수 있다(이미 등록했으면 409)")
  @PostMapping
  public ResponseEntity<ApiResponse<MerchantRegisterResponse>> register(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody MerchantRegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(registrationFacade.register(user, request)));
  }

  @Operation(
      summary = "내 가맹점 조회",
      description = "로그인한 점주의 가맹점을 조회한다(가맹점 번호는 토큰의 회원으로 찾는다). 가맹점이 없으면 404")
  @GetMapping("/me")
  public ApiResponse<MerchantResponse> getMine(@AuthenticationPrincipal AuthUser user) {
    return ApiResponse.ok(merchantService.get(ownedResources.merchantIdOf(user)));
  }

  @Operation(
      summary = "가맹점 수정",
      description =
          "로그인한 점주의 가맹점을 수정한다(가맹점 번호는 토큰의 회원으로 찾는다). "
              + "이름, 주소 중 요청에 포함된 항목만 수정한다. 포함되지 않은 항목은 기존 값을 유지하며, 적립 대상 여부는 수정할 수 없다. "
              + "주소가 바뀌면 서버가 좌표를 다시 정하고(찾지 못하면 400 MER-006, 외부 서비스가 실패하면 좌표를 비운다), 좌표가 비어 있는 가맹점은 수정할 때 변환을 다시 시도한다")
  @PatchMapping("/me")
  public ApiResponse<MerchantResponse> update(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody MerchantUpdateRequest request) {
    return ApiResponse.ok(updateFacade.update(ownedResources.merchantIdOf(user), request));
  }

  @Operation(
      summary = "증빙 제출",
      description =
          "연매출 30억 미만 증빙 서류(revenueProof)를 제출해 적립 제외 가맹점을 적립 대상으로 바꾼다. 이미 적립 대상이면 같은 결과를 돌려준다. "
              + "제출한 뒤의 결제부터 적용되고 이미 끝난 결제는 바뀌지 않는다. 해지 신청 또는 완료된 가맹점은 409(MER-003, MER-004)이다")
  @PutMapping("/me/revenue-proof")
  public ApiResponse<MerchantResponse> submitRevenueProof(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody MerchantRevenueProofRequest request) {
    return ApiResponse.ok(merchantService.submitRevenueProof(ownedResources.merchantIdOf(user)));
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
