package com.back.nbe1214finalhot6.domain.merchant.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.merchant.dto.ClosurePreviewResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.ClosureResponse;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantClosureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "가맹점", description = "가맹점 등록·수정·검색·해지 관련 API")
@RestController
@RequestMapping("/api/merchants/me/closure")
@RequiredArgsConstructor
public class MerchantClosureController {

  private final MerchantClosureService closureService;
  private final OwnedResources ownedResources;

  @Operation(
      summary = "해지 신청 전 정산 현황 조회",
      description =
          "로그인한 점주의 가맹점이 해지를 신청하면 어떻게 되는지 미리 보여 준다(가맹점 번호는 토큰의 회원으로 찾는다). "
              + "아직 계좌로 나가지 않은 정산금(unpaidAmount), 그중 갚을 금액에서 차감할 수 있는 정산금(offsettableAmount), "
              + "갚아야 하는 금액(carryDebt), 계좌 잔액(참고용)과 신청 가능 여부(closable)를 담는다. "
              + "갚을 금액은 마지막 일 정산 기준이라 그 뒤의 취소는 반영되지 않을 수 있다")
  @GetMapping("/preview")
  public ApiResponse<ClosurePreviewResponse> preview(@AuthenticationPrincipal AuthUser user) {
    return ApiResponse.ok(closureService.preview(ownedResources.merchantIdOf(user)));
  }

  @Operation(
      summary = "가맹점 해지 신청",
      description =
          "로그인한 점주의 가맹점 해지를 신청한다. 신청하면 곧바로 새 결제와 QR 생성이 막히고 철회할 수 없다. "
              + "이미 해지 진행 중이면 409(MER-003), 해지됐으면 409(MER-004), 갚을 금액이 아직 지급하지 않은 정산금보다 많으면 409(MER-005)")
  @PostMapping
  public ResponseEntity<ApiResponse<ClosureResponse>> request(
      @AuthenticationPrincipal AuthUser user) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(closureService.request(ownedResources.merchantIdOf(user))));
  }
}
