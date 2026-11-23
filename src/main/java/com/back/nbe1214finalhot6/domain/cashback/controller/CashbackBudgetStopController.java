package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.EmergencyStopRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 적립 중단(운영자)", description = "운영자가 직접 적립을 공지 후 중단하거나 긴급 중단하는 API")
@RestController
@RequestMapping("/api/admin/cashback/budget/monthly/{year}/{month}")
@RequiredArgsConstructor
public class CashbackBudgetStopController {

  private final CashbackBudgetStateService stateService;
  private final CashbackBudgetService budgetService;

  @Operation(
      summary = "공지 후 중단",
      description =
          "정상 상태의 월 예산을 공지 상태로 바꾸고 중단 예약 시각을 오늘 + 3일 00:00(KST)으로 정한다. 적립은 예약 시각까지 계속된다."
              + " 이미 공지 상태면 예약 시각을 바꾸지 않고 현재 상태를 돌려주며, 이미 중단·마감된 달은 409(CSH-012)다")
  @PostMapping("/notice-stop")
  public ResponseEntity<ApiResponse<MonthlyBudgetResponse>> noticeStop(
      @PathVariable int year, @PathVariable int month) {
    stateService.noticeStopManually(year, month);
    return ResponseEntity.ok(ApiResponse.ok(budgetService.getMonthly(year, month)));
  }

  @Operation(
      summary = "긴급 중단",
      description =
          "정상·공지 상태의 월 예산을 즉시 적립 중단으로 바꾼다. 결제는 그대로 되고 적립만 0원이 된다. 실수 방지를 위해 본문에"
              + " {\"confirm\": true} 가 있어야 하며 없으면 400이다. 이미 중단·마감된 달은 409(CSH-012)다")
  @PostMapping("/emergency-stop")
  public ResponseEntity<ApiResponse<MonthlyBudgetResponse>> emergencyStop(
      @PathVariable int year,
      @PathVariable int month,
      @Valid @RequestBody EmergencyStopRequest request) {
    stateService.emergencyStopManually(year, month);
    return ResponseEntity.ok(ApiResponse.ok(budgetService.getMonthly(year, month)));
  }
}
