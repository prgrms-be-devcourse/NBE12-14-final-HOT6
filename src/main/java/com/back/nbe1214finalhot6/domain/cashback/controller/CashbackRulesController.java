package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRulesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 조회(이용자)", description = "내 월 적립 한도와 적립 내역 조회 API. 지갑은 토큰의 회원으로 찾는다")
@RestController
@RequestMapping("/api/cashback/rules")
@RequiredArgsConstructor
public class CashbackRulesController {

  private final CashbackProperties properties;

  @Operation(
      summary = "적립 지급 규칙 조회",
      description = "적립 지급 지연 일수를 돌려준다. 영수증의 지급 예정일 안내 문구가 쓴다. 값은 서버 설정이다")
  @GetMapping
  public ApiResponse<PayoutRulesResponse> payoutRules() {
    return ApiResponse.ok(new PayoutRulesResponse(properties.payoutDelayDays()));
  }
}
