package com.back.nbe1214finalhot6.setting.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 운영자 설정(SET) 에러 코드. 번호는 공통 에러 코드 배정에 따라 조정할 수 있다. */
@Getter
@RequiredArgsConstructor
public enum SettingErrorCode implements ErrorCode {
  INVALID_SETTING("SET-001", HttpStatus.BAD_REQUEST, "설정 키 또는 값이 올바르지 않습니다"),
  SCHEDULE_ALREADY_EXISTS("SET-002", HttpStatus.CONFLICT, "예약된 변경이 있습니다. 예약을 취소한 뒤 다시 시도해 주세요"),
  SCHEDULE_NOT_FOUND("SET-003", HttpStatus.NOT_FOUND, "취소할 예약이 없습니다");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
