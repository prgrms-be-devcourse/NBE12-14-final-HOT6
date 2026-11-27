package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastAccuracyResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackForecastAccuracyService;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 소진 예측(운영자)", description = "월 예산의 예상 소진일과 예상 선을 계산하는 API")
@RestController
@RequestMapping("/api/admin/cashback/forecast")
@RequiredArgsConstructor
public class CashbackForecastController {

  private final CashbackForecastService forecastService;
  private final CashbackForecastAccuracyService accuracyService;

  @Operation(
      summary = "월 예산 소진 예측",
      description =
          "기준일(오늘)을 뺀 최근 7일의 일평균 적립 확보액을 수요로 예상 소진일·대비금 도달일·예상 선을 계산한다."
              + " 읽기만 하며 이번 달만 계산한다. year·month 를 생략하면 이번 달이고, 하나만 주면 400 이다."
              + " 응답에 사용한 가정(수요 기준·배율·범위 폭)과 모델 버전이 함께 나간다")
  @GetMapping
  public ApiResponse<ForecastResponse> forecast(
      @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month) {
    return ApiResponse.ok(forecastService.forecast(year, month));
  }

  @Operation(
      summary = "소진 예측 오차",
      description =
          "하루 한 번 저장한 예측의 하루 예상 수요(중간)와 그날 실제 적립 확보액(취소 제외)을 비교한다."
              + " 오늘을 뺀 최근 30일 중 예측을 저장한 날만 최근 날짜부터 주고, 차이 크기의 평균과 범위(느림~빠름) 안이었던 날 수를 함께 준다")
  @GetMapping("/accuracy")
  public ApiResponse<ForecastAccuracyResponse> accuracy() {
    return ApiResponse.ok(accuracyService.accuracy());
  }
}
