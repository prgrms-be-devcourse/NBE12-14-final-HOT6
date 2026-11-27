package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.AdminEarningResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackEarningAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 적립 내역(운영자)", description = "모든 이용자의 적립 내역 조회 API")
@RestController
@RequestMapping("/api/admin/cashback/earnings")
@RequiredArgsConstructor
public class CashbackEarningAdminController {

  private final CashbackEarningAdminService earningService;

  @Operation(
      summary = "적립 내역 전체 조회",
      description =
          "최근 접수순. status(PENDING / PAID / CANCELED / CLAWED_BACK / EXCLUDED), walletId(지갑 번호),"
              + " from·to(결제 접수일, 양 끝 포함)로 거른다. 조건을 주지 않으면 전체다. 시작일이 종료일보다 늦으면 400(CSH-016)")
  @GetMapping
  public ApiResponse<PageResponse<AdminEarningResponse>> earnings(
      @RequestParam(required = false) EarningStatus status,
      @RequestParam(required = false) @Min(1) Long walletId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size) {
    return ApiResponse.ok(earningService.search(status, walletId, from, to, page, size));
  }
}
