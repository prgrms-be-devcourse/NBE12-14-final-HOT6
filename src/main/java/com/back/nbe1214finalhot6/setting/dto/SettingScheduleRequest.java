package com.back.nbe1214finalhot6.setting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 설정 변경 예약 요청. 적용일 00:00 부터 새 값을 쓴다.
 *
 * @param value 새 값. null 이면 기본값으로 되돌리는 예약
 * @param effectiveDate 적용일. 내일 이후여야 한다
 * @param notice 예약을 알리는 공지
 */
public record SettingScheduleRequest(
    Long value, @NotNull LocalDate effectiveDate, @NotNull @Valid SettingNoticeRequest notice) {}
