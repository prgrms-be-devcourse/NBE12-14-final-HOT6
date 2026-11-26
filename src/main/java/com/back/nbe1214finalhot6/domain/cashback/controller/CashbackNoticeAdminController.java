package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeAdminResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeUpdateRequest;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캐시백 공지(운영자)", description = "운영자가 지금까지의 공지를 보고 문구와 홈 노출을 고치는 API")
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class CashbackNoticeAdminController {

  private final CashbackNoticeService noticeService;

  @Operation(summary = "공지 전체 목록 조회", description = "예약 취소로 거둔 공지를 포함해 최근 등록순으로 돌려준다")
  @GetMapping
  public ApiResponse<PageResponse<NoticeAdminResponse>> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size) {
    return ApiResponse.ok(noticeService.listForAdmin(page, size));
  }

  @Operation(
      summary = "공지 수정",
      description =
          "게시 중인 공지의 제목, 본문, 홈 노출, 홈 종료일을 고친다. 요율·한도 값과 적용일은 바꿀 수 없다. 거둔 공지나 잘못된 내용은 400(CSH-014),"
              + " 없는 공지는 404(CSH-015)다")
  @PatchMapping("/{noticeId}")
  public ApiResponse<NoticeAdminResponse> update(
      @PathVariable Long noticeId, @Valid @RequestBody NoticeUpdateRequest request) {
    return ApiResponse.ok(noticeService.update(noticeId, request));
  }
}
