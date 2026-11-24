package com.back.nbe1214finalhot6.domain.payment.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.payment.dto.MerchantPaymentsResponse;
import com.back.nbe1214finalhot6.domain.payment.service.MerchantPaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제", description = "QR 결제 승인, 검증, 조회 및 취소 관련 API")
@RestController
@RequestMapping("/api/merchants/me/payments")
@RequiredArgsConstructor
public class MerchantPaymentController {

  private final MerchantPaymentQueryService queryService;
  private final OwnedResources ownedResources;

  @Operation(
      summary = "사장님 결제 내역·매출 조회",
      description =
          "로그인한 점주의 가맹점 결제 내역을 기간별로 조회한다(가맹점 번호는 토큰의 회원으로 찾는다). "
              + "from, to 는 yyyy-MM-dd(양 끝 포함, 최대 31일)이고 모두 생략하면 오늘이다. "
              + "응답에는 기간의 결제 건수·금액, 취소 건수·금액, 순매출(결제 - 취소)과 결제 목록(최신순, 페이징)이 담긴다. "
              + "결제는 완료한 날, 취소는 취소한 날에 센다(일 정산과 같은 기준). 처리 중·실패한 결제는 포함하지 않는다")
  @GetMapping
  public ApiResponse<MerchantPaymentsResponse> get(
      @AuthenticationPrincipal AuthUser user,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size) {
    return ApiResponse.ok(
        queryService.get(ownedResources.merchantIdOf(user), from, to, page, size));
  }
}
