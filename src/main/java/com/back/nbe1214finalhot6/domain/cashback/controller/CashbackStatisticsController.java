package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.DailyEarningTrendResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyLimitUsageResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 적립 통계(운영자)", description = "월 한도 사용 분포와 일별 적립액 추이를 조회하는 API")
@RestController
@RequestMapping("/api/admin/cashback/statistics")
@RequiredArgsConstructor
public class CashbackStatisticsController {

  private final CashbackStatisticsService statisticsService;

  @Operation(
      summary = "월 적립 한도 사용 분포",
      description =
          "이번 달에 적립한 이용자를 지금 적용 중인 월 적립 한도의 사용률 구간(25% 미만, 25~50%, 50~75%, 75~100% 미만, 도달)으로"
              + " 센다. 적립 예정을 포함하며 사용량이 0 인 이용자는 빼고, 이용자를 알 수 있는 값은 주지 않는다")
  @GetMapping("/monthly-limit")
  public ApiResponse<MonthlyLimitUsageResponse> monthlyLimit() {
    return ApiResponse.ok(statisticsService.monthlyLimitUsage());
  }

  @Operation(
      summary = "일별 적립액 추이",
      description =
          "오늘까지 30일의 날짜별 신규 적립액, 직전 7일 평균, 적용 요율을 오래된 날짜부터 준다. 적립 예정과 지급 완료를 접수한 날로 세므로"
              + " 나중에 취소된 건은 그날 금액에서도 빠진다. 오늘의 평균은 소진 예측의 최근 7일 평균과 같다")
  @GetMapping("/daily-earnings")
  public ApiResponse<DailyEarningTrendResponse> dailyEarnings() {
    return ApiResponse.ok(statisticsService.dailyEarnings());
  }
}
