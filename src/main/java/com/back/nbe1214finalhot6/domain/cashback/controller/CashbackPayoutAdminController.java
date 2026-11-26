package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunDetailResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunSummaryResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutStatusResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.PayoutTrigger;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackPayoutService;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackPayoutStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 지급(운영자)", description = "캐시백 적립 지급 배치 현황 조회와 수동 실행 API")
@RestController
@RequestMapping("/api/admin/cashback/payouts")
@RequiredArgsConstructor
public class CashbackPayoutAdminController {

  private final CashbackPayoutService payoutService;
  private final CashbackPayoutStatusService statusService;

  @Operation(
      summary = "지급 배치 현황",
      description =
          "지급 대기 건수·금액, 그중 지금 지급할 수 있는 건수·금액, 다음 지급 예정 시각을 돌려준다")
  @GetMapping("/status")
  public ApiResponse<PayoutStatusResponse> status() {
    return ApiResponse.ok(statusService.status());
  }

  @Operation(
      summary = "지급 배치 실행 목록",
      description = "최근 실행 순으로 돌려준다. 자동 실행은 지급이나 실패가 있었던 것만 남는다")
  @GetMapping("/runs")
  public ApiResponse<PageResponse<PayoutRunSummaryResponse>> runs(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size) {
    return ApiResponse.ok(statusService.runs(page, size));
  }

  @Operation(
      summary = "지급 배치 실행 상세",
      description = "처리한 적립 건과 실패 사유를 돌려준다. 실패, 넘어감, 지급 순이다. 없는 실행이면 404(CSH-013)")
  @GetMapping("/runs/{runId}")
  public ApiResponse<PayoutRunDetailResponse> runDetail(@PathVariable Long runId) {
    return ApiResponse.ok(statusService.detail(runId));
  }

  @Operation(
      summary = "지급 배치 수동 실행",
      description =
          "지급 예정 시각이 지난 적립 예정 건을 지금 지갑에 입금한다. 스케줄러와 같은 처리이며 같은 건을 두 번 지급하지 않는다."
              + " 처리한 건수(paid), 넘어간 건수(skipped), 실패해 다음 배치로 미룬 건수(failed)를 돌려준다")
  @PostMapping("/run")
  public ApiResponse<PayoutRunResponse> run() {
    return ApiResponse.ok(payoutService.runAndRecord(PayoutTrigger.MANUAL));
  }
}
