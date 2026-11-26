package com.back.nbe1214finalhot6.setting.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.setting.dto.SettingHistoryResponse;
import com.back.nbe1214finalhot6.setting.dto.SettingResetRequest;
import com.back.nbe1214finalhot6.setting.dto.SettingResponse;
import com.back.nbe1214finalhot6.setting.dto.SettingScheduleRequest;
import com.back.nbe1214finalhot6.setting.dto.SettingUpdateRequest;
import com.back.nbe1214finalhot6.setting.service.OperatorSettingAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "운영자 설정", description = "이용자 월 적립 한도, 지갑 보유·월 충전 한도 같은 운영 설정 API")
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class OperatorSettingAdminController {

  private final OperatorSettingAdminService settingService;

  @Operation(
      summary = "설정 전체 조회",
      description = "모든 키의 현재 값, 출처(기본값/운영자 설정), 기본값, 최솟값, 하드 상한, 수정 시각, 대기 중인 예약을 돌려준다")
  @GetMapping
  public ResponseEntity<ApiResponse<List<SettingResponse>>> list() {
    return ResponseEntity.ok(ApiResponse.ok(settingService.list()));
  }

  @Operation(summary = "설정 변경 이력 조회", description = "모든 설정의 변경 이력을 최근 변경순으로 돌려준다. 적용 전인 예약은 담기지 않는다")
  @GetMapping("/history")
  public ResponseEntity<ApiResponse<PageResponse<SettingHistoryResponse>>> history(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size) {
    return ResponseEntity.ok(ApiResponse.ok(settingService.history(page, size)));
  }

  @Operation(
      summary = "설정 값 변경",
      description =
          "바로 바꾼다. 값은 키의 최솟값 이상, 하드 상한 이하여야 한다(상한과 같은 값 허용). 현재 값과 같으면 아무것도 바꾸지 않고, 바꾸면 이후부터 적용한다."
              + " 공지(notice)가 필수이며 값이 바뀌면 함께 등록한다. 대기 중인 예약은 남는다")
  @PutMapping("/{key}")
  public ResponseEntity<ApiResponse<SettingResponse>> put(
      @PathVariable String key, @Valid @RequestBody SettingUpdateRequest request) {
    return ResponseEntity.ok(
        ApiResponse.ok(settingService.put(key, request.value(), request.notice())));
  }

  @Operation(
      summary = "설정을 기본값으로 되돌림",
      description = "바로 되돌린다. 본문의 공지(notice)가 필수이며 값이 바뀌면 함께 등록한다. 운영자가 바꾼 적이 없는 키면 아무것도 바꾸지 않는다")
  @DeleteMapping("/{key}")
  public ResponseEntity<ApiResponse<SettingResponse>> reset(
      @PathVariable String key,
      @Valid @RequestBody SettingResetRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(settingService.reset(key, request.notice())));
  }

  @Operation(
      summary = "설정 변경 예약",
      description =
          "적용일(내일 이후) 00:00 부터 새 값을 쓰고, 공지를 함께 등록한다. value 를 비우면 기본값으로 되돌리는 예약이다."
              + " 설정마다 예약은 하나다(이미 있으면 409, SET-002)")
  @PostMapping("/{key}/schedule")
  public ResponseEntity<ApiResponse<SettingResponse>> schedule(
      @PathVariable String key, @Valid @RequestBody SettingScheduleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(settingService.schedule(key, request)));
  }

  @Operation(
      summary = "설정 변경 예약 취소",
      description = "아직 적용되지 않은 예약을 취소하고 그 공지를 거둔다. 없으면 404(SET-003)")
  @DeleteMapping("/{key}/schedule")
  public ResponseEntity<ApiResponse<SettingResponse>> cancelSchedule(@PathVariable String key) {
    return ResponseEntity.ok(ApiResponse.ok(settingService.cancelSchedule(key)));
  }
}
