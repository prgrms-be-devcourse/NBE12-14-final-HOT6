package com.back.nbe1214finalhot6.setting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 기본값으로 되돌릴 때의 요청.
 *
 * @param notice 함께 등록할 공지
 */
public record SettingResetRequest(@NotNull @Valid SettingNoticeRequest notice) {}
