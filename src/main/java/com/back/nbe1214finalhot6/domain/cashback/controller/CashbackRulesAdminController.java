package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackRulesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 운영 규칙(운영자)", description = "운영 화면이 안내 문구에 쓰는 서버 설정값 조회 API")
@RestController
@RequestMapping("/api/admin/cashback/rules")
@RequiredArgsConstructor
public class CashbackRulesAdminController {

  private final CashbackProperties properties;

  @Operation(
      summary = "캐시백 운영 규칙 조회",
      description =
          "대비금 비율, 긴급선 비율, 공지 후 중단 일수, 지급 지연 일수를 돌려준다. 운영 화면의 안내 문구가 서버 설정과 어긋나지 않게 쓴다")
  @GetMapping
  public ApiResponse<CashbackRulesResponse> rules() {
    return ApiResponse.ok(CashbackRulesResponse.from(properties));
  }
}
