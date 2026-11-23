package com.back.nbe1214finalhot6.domain.reconciliation.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationResultResponse;
import com.back.nbe1214finalhot6.domain.reconciliation.service.ReconciliationQueryService;
import com.back.nbe1214finalhot6.domain.reconciliation.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대사", description = "정산 원장 대사 운영자 API")
@RestController
@RequestMapping("/api/admin/settlements/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

  private final ReconciliationQueryService queryService;
  private final ReconciliationService reconciliationService;
  private final Clock clock;

  @Operation(
      summary = "대사 결과 조회",
      description =
          "대상 날짜가 기간 안에 있는 대사 결과를 최근 실행 순으로 조회한다. "
              + "from, to 는 yyyy-MM-dd 이고 최대 31일까지 조회할 수 있다")
  @GetMapping
  public ApiResponse<List<ReconciliationResultResponse>> getResults(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ApiResponse.ok(queryService.getResults(from, to));
  }

  @Operation(
      summary = "대사 수동 실행",
      description =
          "날짜를 지정해 모든 종류의 대사를 실행하고 저장한다. "
              + "날짜를 생략하면 어제를 대사한다. 오늘 이후는 실행할 수 없다")
  @PostMapping("/run")
  public ApiResponse<List<ReconciliationResultResponse>> run(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    LocalDate target = date != null ? date : LocalDate.now(clock).minusDays(1);
    return ApiResponse.ok(
        reconciliationService.run(target).stream()
            .map(ReconciliationResultResponse::from)
            .toList());
  }
}
