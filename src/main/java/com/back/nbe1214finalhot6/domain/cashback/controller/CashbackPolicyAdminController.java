package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyHistoryResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReservationResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
              + " 공지(notice)는 필수이며 그대로 게시한다. 사유(reason, 200자까지)는 필수이며 그 시점의 예산·예측과 함께 변경 이력에 남는다. 만들어진 요율 구간(월 말까지)을 돌려준다")
  @PostMapping("/reductions")
  public ResponseEntity<ApiResponse<PolicyResponse>> reduce(
      @Valid @RequestBody RateReductionRequest request,
      @AuthenticationPrincipal AuthUser user) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(policyService.reduceRate(request, user.userId())));
  }

  @Operation(
      summary = "예약한 요율 인하 조회",
      description = "아직 적용되지 않은 구간의 요율, 적용 시각, 사유, 공지를 돌려준다. 적용이 시작됐으면 409(CSH-017)")
  @GetMapping("/{policyId}/reduction")
  public ResponseEntity<ApiResponse<RateReservationResponse>> reservation(
      @PathVariable Long policyId) {
    return ResponseEntity.ok(ApiResponse.ok(policyService.getReservation(policyId)));
  }

  @Operation(
      summary = "예약한 요율 인하 수정",
      description =
          "아직 적용되지 않은 구간의 요율, 적용 시각(같은 달 안), 사유, 공지를 수정한다. 새 요율은 그 시각의 이전 요율보다 낮아야 하며 월 인하"
              + " 횟수는 늘지 않는다. 적용이 시작됐으면 409(CSH-017)")
  @PutMapping("/{policyId}/reduction")
  public ResponseEntity<ApiResponse<PolicyResponse>> revise(
      @PathVariable Long policyId,
      @Valid @RequestBody RateReductionRequest request,
      @AuthenticationPrincipal AuthUser user) {
    return ResponseEntity.ok(
        ApiResponse.ok(policyService.reviseReduction(policyId, request, user.userId())));
  }

  @Operation(summary = "정책 목록 조회", description = "시작이 늦은 순으로 돌려준다")
  @GetMapping
  public ResponseEntity<ApiResponse<List<PolicyResponse>>> list() {
    return ResponseEntity.ok(ApiResponse.ok(policyService.findAll()));
  }

  @Operation(
      summary = "정책 변경 이력 조회",
      description =
          "요율 구간을 만들거나 바꾼 이력을 최근 변경순으로 돌려준다. policyId 를 주면 그 구간의 이력만 본다."
              + " 변경 종류 CREATE 는 구간 생성(변경 전 값 없음), UPDATE 는 기존 구간의 값(종료 시각 등) 변경이다")
  @GetMapping("/history")
  public ResponseEntity<ApiResponse<PageResponse<PolicyHistoryResponse>>> history(
      @RequestParam(required = false) Long policyId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size) {
    return ResponseEntity.ok(ApiResponse.ok(policyService.findHistory(policyId, page, size)));
  }

  @Operation(summary = "정책 단건 조회")
  @GetMapping("/{policyId}")
  public ResponseEntity<ApiResponse<PolicyResponse>> get(@PathVariable Long policyId) {
    return ResponseEntity.ok(ApiResponse.ok(policyService.get(policyId)));
  }
}
