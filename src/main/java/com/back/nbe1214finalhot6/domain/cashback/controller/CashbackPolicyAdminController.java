package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 정책(운영자)", description = "캐시백 요율 인하와 정책(요율 구간) 조회 API")
@RestController
@RequestMapping("/api/admin/cashback/policies")
@RequiredArgsConstructor
public class CashbackPolicyAdminController {

  private final CashbackPolicyService policyService;

  @Operation(
      summary = "요율 인하",
      description =
          "적용 시각이 속한 달 안에서 요율을 낮춘다. 새 요율(소수 비율, 3% = 0.03)은 적용 시각의 현재 요율보다 낮아야 하고,"
              + " 인하는 한 달에 설정한 횟수까지만 할 수 있다. 적용 시각은 현재 분 + 예고 일수 이후여야 하며 그 달의 월 예산이 있어야 한다."
              + " 만들어진 요율 구간(월 말까지)을 돌려준다")
  @PostMapping("/reductions")
  public ResponseEntity<ApiResponse<PolicyResponse>> reduce(
      @Valid @RequestBody RateReductionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(policyService.reduceRate(request)));
  }

  @Operation(summary = "정책 목록 조회", description = "시작이 늦은 순으로 돌려준다")
  @GetMapping
  public ResponseEntity<ApiResponse<List<PolicyResponse>>> list() {
    return ResponseEntity.ok(ApiResponse.ok(policyService.findAll()));
  }

  @Operation(summary = "정책 단건 조회")
  @GetMapping("/{policyId}")
  public ResponseEntity<ApiResponse<PolicyResponse>> get(@PathVariable Long policyId) {
    return ResponseEntity.ok(ApiResponse.ok(policyService.get(policyId)));
  }
}
