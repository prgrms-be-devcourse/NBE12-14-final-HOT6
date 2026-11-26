package com.back.nbe1214finalhot6.domain.merchant.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.ClosureSettlementRunResult;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantClosureSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "가맹점", description = "가맹점 등록·수정·검색·해지 관련 API")
@RestController
@RequestMapping("/api/admin/merchants/closures")
@RequiredArgsConstructor
public class MerchantClosureAdminController {

  private final MerchantClosureSettlementService settlementService;

  @Operation(
      summary = "가맹점 해지 정산 수동 실행",
      description =
          "해지를 신청한 가맹점을 정산하고 해지를 끝낸다. 신청한 날 전체를 정산하므로 신청 다음 날부터 처리되고, 그전이면 대기로 센다. "
              + "갚을 금액이 정산금보다 많은 가맹점은 해지 진행 중으로 남기고 보류 목록에 담는다. "
              + "이미 끝난 가맹점은 다시 처리하지 않으므로 여러 번 실행해도 중복되지 않는다")
  @PostMapping("/run")
  public ApiResponse<ClosureSettlementRunResult> run() {
    return ApiResponse.ok(settlementService.run());
  }
}
