package com.back.nbe1214finalhot6.setting.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 설정 값 변경 요청.
 *
 * @param value 새 값. 키의 최솟값 이상, 하드 상한 이하여야 한다
 */
public record SettingUpdateRequest(@NotNull Long value) {}
