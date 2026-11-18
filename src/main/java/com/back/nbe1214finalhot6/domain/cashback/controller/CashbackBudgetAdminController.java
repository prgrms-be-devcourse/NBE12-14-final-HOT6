package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
