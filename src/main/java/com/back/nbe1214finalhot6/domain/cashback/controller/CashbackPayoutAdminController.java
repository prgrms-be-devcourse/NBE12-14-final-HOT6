package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackPayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 지급(운영자)", description = "캐시백 적립 지급 배치 수동 실행 API")
@RestController
@RequestMapping("/api/admin/cashback/payouts")
@RequiredArgsConstructor
public class CashbackPayoutAdminController {

  private final CashbackPayoutService payoutService;

  @Operation(
      summary = "지급 배치 수동 실행",
      description =
          "지급 예정 시각이 지난 적립 예정 건을 지금 지갑에 입금한다. 스케줄러와 같은 처리이며 같은 건을 두 번 지급하지 않는다."
              + " 처리한 건수(paid), 넘어간 건수(skipped), 실패해 다음 배치로 미룬 건수(failed)를 돌려준다")
  @PostMapping("/run")
  public ApiResponse<PayoutRunResponse> run() {
    return ApiResponse.ok(payoutService.run());
  }
}
