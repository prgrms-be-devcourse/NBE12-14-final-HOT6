package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeType;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 공지(이용자)", description = "이용자에게 보이는 공지(요율·한도 변경, 적립 중단) 조회 API")
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class CashbackNoticeController {

  private final CashbackNoticeService noticeService;

  @Operation(
      summary = "캐시백 공지 목록 조회",
      description =
          "게시 중인 공지를 최근 등록순으로 돌려준다. type 으로 정책 변경 예고(POLICY_CHANGE)·소진 예고(BUDGET_NOTICE)·긴급 중단(EMERGENCY_STOP)·"
              + "중단 예정 취소(STOP_CANCELED)·한도 변경(LIMIT_CHANGE)만 볼 수 있다. 예고인지 이미 적용됐는지는"
              + " 적용 시작일(effectiveFrom)과 오늘 날짜를 비교해서 판단한다")
  @GetMapping
  public ApiResponse<PageResponse<NoticeResponse>> list(
      @RequestParam(required = false) NoticeType type,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size) {
    return ApiResponse.ok(noticeService.list(type, page, size));
  }

  @Operation(summary = "홈 공지 조회", description = "홈에 띄울 공지다. 거두지 않았고 종료일이 지나지 않은 것 중 최근 5건이다")
  @GetMapping("/home")
  public ApiResponse<List<NoticeResponse>> home() {
    return ApiResponse.ok(noticeService.listForHome());
  }
}
