package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 예산(운영자)", description = "캐시백 연·월 예산 관리 API")
@RestController
@RequestMapping("/api/admin/cashback/budget")
@RequiredArgsConstructor
public class CashbackBudgetAdminController {

  private final CashbackBudgetService budgetService;

  @Operation(
      summary = "연 예산 등록·수정",
      description = "연도별 총액을 등록한다. 같은 연도가 이미 있으면 총액을 바꾼다")
  @PutMapping("/annual/{year}")
  public ResponseEntity<ApiResponse<AnnualBudgetResponse>> putAnnual(
      @PathVariable int year, @Valid @RequestBody AnnualBudgetRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(budgetService.putAnnual(year, request)));
  }

  @Operation(summary = "연 예산 조회")
  @GetMapping("/annual/{year}")
  public ResponseEntity<ApiResponse<AnnualBudgetResponse>> getAnnual(@PathVariable int year) {
    return ResponseEntity.ok(ApiResponse.ok(budgetService.getAnnual(year)));
  }

  @Operation(
      summary = "월 예산 배정·수정",
      description = "연 예산 안에서 월 배정을 등록한다. 같은 연·월이 있으면 배정을 바꾸고 대비금을 다시 확정한다")
  @PutMapping("/monthly/{year}/{month}")
  public ResponseEntity<ApiResponse<MonthlyBudgetResponse>> putMonthly(
      @PathVariable int year,
      @PathVariable int month,
      @Valid @RequestBody MonthlyBudgetRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(budgetService.putMonthly(year, month, request)));
  }

  @Operation(summary = "월 예산 조회")
  @GetMapping("/monthly/{year}/{month}")
  public ResponseEntity<ApiResponse<MonthlyBudgetResponse>> getMonthly(
      @PathVariable int year, @PathVariable int month) {
    return ResponseEntity.ok(ApiResponse.ok(budgetService.getMonthly(year, month)));
  }

  @Operation(summary = "연도별 월 예산 목록", description = "배정한 달만 월 순서로 돌려준다")
  @GetMapping("/monthly")
  public ResponseEntity<ApiResponse<List<MonthlyBudgetResponse>>> listMonthly(
      @RequestParam int year) {
    return ResponseEntity.ok(ApiResponse.ok(budgetService.listMonthly(year)));
  }
}
