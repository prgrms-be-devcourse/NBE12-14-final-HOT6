package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetMonthSeries;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetTimeseriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 예산 시계열(운영자)", description = "운영자 대시보드의 예산 현황 그래프용 일별 시계열 API")
@RestController
@RequestMapping("/api/admin/cashback/budget/timeseries")
@RequiredArgsConstructor
public class CashbackBudgetTimeseriesController {

  private final CashbackBudgetTimeseriesService timeseriesService;

  @Operation(
      summary = "예산 일별 시계열 조회",
      description =
          "from~to(양 끝 포함, 최대 366일) 기간과 겹치는 월 예산마다 한 시리즈를 돌려준다. 점은 날짜별로 지급·확보·남은 예산(그날 끝의"
              + " 누적 값, 월 시작부터 계산)을 담고 오늘까지만 만든다. 지급·취소는 접수 며칠 뒤에 일어나므로 월 말 이후 마지막 변동 날짜까지"
              + " 이어진다. 겹치는 월 예산이 없으면 빈 목록이다")
  @GetMapping
  public ApiResponse<List<BudgetMonthSeries>> timeseries(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ApiResponse.ok(timeseriesService.getTimeseries(from, to));
  }
}
