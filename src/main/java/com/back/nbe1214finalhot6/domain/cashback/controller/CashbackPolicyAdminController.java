package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyCreateRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
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

@Tag(name = "캐시백 정책(운영자)", description = "캐시백 요율·기간 정책 관리 API")
@RestController
@RequestMapping("/api/admin/cashback/policies")
@RequiredArgsConstructor
public class CashbackPolicyAdminController {

  private final CashbackPolicyService policyService;

  @Operation(
      summary = "정책 등록",
      description =
          "요율(소수 비율, 3% = 0.03)과 적용 기간 [시작, 종료)을 등록한다. 종료를 생략하면 무기한이며, 기존 정책과 기간이 겹치면 거부한다")
  @PostMapping
  public ResponseEntity<ApiResponse<PolicyResponse>> create(
      @Valid @RequestBody PolicyCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(policyService.create(request)));
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
