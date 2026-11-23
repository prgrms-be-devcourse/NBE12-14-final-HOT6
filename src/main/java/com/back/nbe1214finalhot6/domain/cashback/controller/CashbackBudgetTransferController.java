package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetTransferRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetTransferResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 예산 이월(운영자)", description = "남은 월 예산을 다음 달로 넘기는 API")
@RestController
@RequestMapping("/api/admin/cashback/budget/transfers")
@RequiredArgsConstructor
public class CashbackBudgetTransferController {

  private final CashbackBudgetTransferService transferService;

  @Operation(
      summary = "월 예산 이월",
      description =
          "year·month 달의 남은 예산에서 amount 를 다음 달로 넘긴다. 부분 이월이 되고, 금액은 0보다 크고 남은 예산 이하여야 한다."
              + " 12월은 다음 해 1월로 넘어가며 이때는 연 예산도 함께 맞춘다. 넘기는 달과 받는 달의 월 예산이 모두 있어야 한다."
              + " 적립 중단·마감 상태와 상관없이 넘길 수 있다")
  @PostMapping
  public ResponseEntity<ApiResponse<BudgetTransferResponse>> transfer(
      @Valid @RequestBody BudgetTransferRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(transferService.transfer(request)));
  }

  @Operation(summary = "월 예산 이월 이력 조회", description = "해당 연도의 월에서 넘긴 이력을 최근 이월순으로 돌려준다")
  @GetMapping
  public ResponseEntity<ApiResponse<List<BudgetTransferResponse>>> list(@RequestParam int year) {
    return ResponseEntity.ok(ApiResponse.ok(transferService.list(year)));
  }
}
