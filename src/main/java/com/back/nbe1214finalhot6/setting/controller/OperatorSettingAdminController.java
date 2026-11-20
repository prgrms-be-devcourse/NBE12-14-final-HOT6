package com.back.nbe1214finalhot6.setting.controller;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.setting.dto.SettingResponse;
import com.back.nbe1214finalhot6.setting.dto.SettingUpdateRequest;
import com.back.nbe1214finalhot6.setting.service.OperatorSettingAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "운영자 설정", description = "이용자 월 적립 한도, 지갑 보유·월 충전 한도 같은 운영 설정 API")
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class OperatorSettingAdminController {

  private final OperatorSettingAdminService settingService;

  @Operation(
      summary = "설정 전체 조회",
      description = "모든 키의 현재 값, 출처(기본값/운영자 설정), 기본값, 최솟값, 하드 상한, 수정 시각을 돌려준다")
  @GetMapping
  public ResponseEntity<ApiResponse<List<SettingResponse>>> list() {
    return ResponseEntity.ok(ApiResponse.ok(settingService.list()));
  }

  @Operation(
      summary = "설정 값 변경",
      description =
          "값은 키의 최솟값 이상, 하드 상한 이하여야 한다(상한과 같은 값 허용). 현재 값과 같으면 아무것도 바꾸지 않고, 바꾸면 이후부터 적용한다")
  @PutMapping("/{key}")
  public ResponseEntity<ApiResponse<SettingResponse>> put(
      @PathVariable String key, @Valid @RequestBody SettingUpdateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(settingService.put(key, request.value())));
  }

  @Operation(summary = "설정을 기본값으로 되돌림", description = "운영자가 바꾼 적이 없는 키면 아무것도 바꾸지 않는다")
  @DeleteMapping("/{key}")
  public ResponseEntity<ApiResponse<SettingResponse>> reset(@PathVariable String key) {
    return ResponseEntity.ok(ApiResponse.ok(settingService.reset(key)));
  }
}
