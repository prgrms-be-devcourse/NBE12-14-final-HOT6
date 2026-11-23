package com.back.nbe1214finalhot6.domain.settlement.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.settlement.dto.DailySettlementResponse;
import com.back.nbe1214finalhot6.domain.settlement.service.DailySettlementQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정산", description = "가맹점 정산 조회 API")
@RestController
@RequestMapping("/api/merchants/{merchantId}/settlements")
@RequiredArgsConstructor
public class DailySettlementController {

  private final DailySettlementQueryService queryService;

  @Operation(
      summary = "일 정산 조회",
      description =
          "기간 안의 일 정산을 날짜순으로 조회한다. from, to 는 yyyy-MM-dd 이고 최대 31일까지 조회할 수 있다. "
              + "정산이 없는 날짜는 포함되지 않는다")
  @GetMapping("/daily")
  public ApiResponse<List<DailySettlementResponse>> getDaily(
      @PathVariable Long merchantId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    // TODO: 인증(COM-03) 머지 후 로그인한 사장님의 ID 와 가맹점의 ownerId 를 비교해 본인 가맹점의 정산만 조회하도록 검사한다.
    return ApiResponse.ok(queryService.getDaily(merchantId, from, to));
  }
}
