package com.back.nbe1214finalhot6.common.idempotency;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import java.util.UUID;

public final class IdempotencyKeys {

  private static final String INVALID_FORMAT_MESSAGE = "Idempotency-Key는 UUID 형식이어야 합니다";

  private IdempotencyKeys() {}

  // Idempotency-Key 헤더 값이 표준 UUID 형식인지 검증한다.
  // 값이 없으면 COM-002, 형식이 올바르지 않으면 COM-001 로 BusinessException 을 던진다.
  public static String validate(String key) {
    if (key == null || key.isBlank()) {
      throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_MISSING);
    }
    try {
      // UUID.fromString 은 "1-1-1-1-1" 같은 비표준 문자열도 통과시키므로 되돌려 비교한다.
      if (!UUID.fromString(key).toString().equalsIgnoreCase(key)) {
        throw new BusinessException(CommonErrorCode.INVALID_REQUEST, INVALID_FORMAT_MESSAGE);
      }
      return key;
    } catch (IllegalArgumentException e) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST, INVALID_FORMAT_MESSAGE);
    }
  }
}
