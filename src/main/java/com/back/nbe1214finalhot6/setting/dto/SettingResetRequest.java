package com.back.nbe1214finalhot6.setting.dto;

import jakarta.validation.Valid;

/**
 * 기본값으로 되돌릴 때의 요청 본문(선택).
 *
 * @param notice 함께 등록할 공지. 생략하면 공지 없이 되돌린다
 */
public record SettingResetRequest(@Valid SettingNoticeRequest notice) {}
