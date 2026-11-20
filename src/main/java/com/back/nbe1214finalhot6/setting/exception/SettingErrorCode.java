package com.back.nbe1214finalhot6.setting.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 운영자 설정(SET) 에러 코드. 번호는 공통 에러 코드 배정에 따라 조정할 수 있다. */
@Getter
@RequiredArgsConstructor
public enum SettingErrorCode implements ErrorCode {
  INVALID_SETTING("SET-001", HttpStatus.BAD_REQUEST, "설정 키 또는 값이 올바르지 않습니다");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
