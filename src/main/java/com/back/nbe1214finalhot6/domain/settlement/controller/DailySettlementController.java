package com.back.nbe1214finalhot6.domain.settlement.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.settlement.dto.DailySettlementResponse;
import com.back.nbe1214finalhot6.domain.settlement.service.DailySettlementQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
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
public class DailySettlementController {

  private final DailySettlementQueryService queryService;
  private final OwnedResources ownedResources;

  @Operation(
      summary = "일 정산 조회",
      description =
          "로그인한 점주의 가맹점 일 정산을 조회한다(가맹점 번호는 토큰의 회원으로 찾는다). "
              + "기간 안의 일 정산을 날짜순으로 조회한다. from, to 는 yyyy-MM-dd 이고 최대 31일까지 조회할 수 있다. "
              + "정산이 없는 날짜는 포함되지 않는다")
  @GetMapping("/daily")
  public ApiResponse<List<DailySettlementResponse>> getDaily(
      @AuthenticationPrincipal AuthUser user,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ApiResponse.ok(queryService.getDaily(ownedResources.merchantIdOf(user), from, to));
  }
}
