package com.back.nbe1214finalhot6.domain.settlement.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementResponse;
import com.back.nbe1214finalhot6.domain.settlement.service.MonthlySettlementQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정산", description = "가맹점 정산 조회 API")
@RestController
@RequestMapping("/api/merchants/me/settlements")
@RequiredArgsConstructor
public class MonthlySettlementController {

  private final MonthlySettlementQueryService queryService;
  private final OwnedResources ownedResources;

  @Operation(
      summary = "월 정산 조회",
      description =
          "로그인한 점주의 가맹점 월 정산(지급) 내역을 정산월 순으로 조회한다(가맹점 번호는 토큰의 회원으로 찾는다). "
              + "from, to 는 yyyy-MM 이고 최대 12개월까지 조회할 수 있다. 지급할 금액이 없던 달은 포함되지 않는다")
  @GetMapping("/monthly")
  public ApiResponse<List<MonthlySettlementResponse>> getMonthly(
      @AuthenticationPrincipal AuthUser user,
      @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
      @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth to) {
    return ApiResponse.ok(queryService.getMonthly(ownedResources.merchantIdOf(user), from, to));
  }
}
