package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackSummaryResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.EarningResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyLimitResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 조회(이용자)", description = "내 월 적립 한도와 적립 내역 조회 API. 지갑은 토큰의 회원으로 찾는다")
@RestController
@RequestMapping("/api/cashback")
@RequiredArgsConstructor
public class CashbackQueryController {

  private final CashbackQueryService queryService;
  private final OwnedResources ownedResources;

  @Operation(
      summary = "이번 달 월 적립 한도 조회",
      description = "이번 달(한국 시간)의 한도, 사용량(적립 예정 포함), 남은 한도를 돌려준다")
  @GetMapping("/monthly-limit")
  public ApiResponse<MonthlyLimitResponse> monthlyLimit(@AuthenticationPrincipal AuthUser user) {
    return ApiResponse.ok(queryService.getMonthlyLimit(ownedResources.walletIdOf(user)));
  }

  @Operation(
      summary = "이번 달 적립 요약",
      description =
          "이번 달(한국 시간, 결제 접수 시각 기준) 적립액·적립 예정·지급 완료 금액과 제외 건수를 돌려준다."
              + " 취소·회수된 건은 금액에 넣지 않는다")
  @GetMapping("/summary")
  public ApiResponse<CashbackSummaryResponse> summary(@AuthenticationPrincipal AuthUser user) {
    return ApiResponse.ok(queryService.getSummary(ownedResources.walletIdOf(user)));
  }

  @Operation(
      summary = "내 적립 내역 조회",
      description =
          "최근 접수순. status 로 예정(PENDING) / 지급 완료(PAID) / 취소(CANCELED) / 회수(CLAWED_BACK)"
              + " / 제외(EXCLUDED)만 볼 수 있다. 제외 건은 적립액 0과 제외 사유를 담는다")
  @GetMapping("/earnings")
  public ApiResponse<PageResponse<EarningResponse>> earnings(
      @AuthenticationPrincipal AuthUser user,
      @RequestParam(required = false) EarningStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size) {
    return ApiResponse.ok(queryService.listEarnings(ownedResources.walletIdOf(user), status, page, size));
  }
}
