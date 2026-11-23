package com.back.nbe1214finalhot6.domain.settlement.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.dto.SettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.service.DailySettlementService;
import com.back.nbe1214finalhot6.domain.settlement.service.MonthlySettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정산", description = "정산 운영자 API")
@RestController
@RequestMapping("/api/admin/settlements")
@RequiredArgsConstructor
public class SettlementAdminController {

  private final DailySettlementService dailySettlementService;
  private final MonthlySettlementService monthlySettlementService;
  private final Clock clock;

  @Operation(
      summary = "일 정산 수동 실행",
      description =
          "날짜를 지정해 그날의 일 정산을 실행한다. 날짜를 생략하면 어제를 정산한다. "
              + "오늘 이후는 실행할 수 없고, 이미 정산된 가맹점은 건너뛰므로 다시 실행해도 중복되지 않는다")
  @PostMapping("/daily/run")
  public ApiResponse<SettlementRunResult> runDaily(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    LocalDate target = date != null ? date : LocalDate.now(clock).minusDays(1);
    return ApiResponse.ok(dailySettlementService.settle(target));
  }

  @Operation(
      summary = "월 정산 수동 실행",
      description =
          "달을 지정해 그 달의 월 정산 건을 만들고 지급한다. 달을 생략하면 지난달을 정산한다. "
              + "해당 달의 일 정산이 모두 끝난 뒤에 실행해야 하며, 이번 달 이후는 실행할 수 없다. "
              + "이미 만든 건과 지급한 건은 건너뛰므로 다시 실행해도 중복되지 않는다")
  @PostMapping("/monthly/run")
  public ApiResponse<MonthlySettlementRunResult> runMonthly(
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    YearMonth target = month != null ? month : YearMonth.now(clock).minusMonths(1);
    return ApiResponse.ok(monthlySettlementService.settle(target));
  }
}
