package com.back.nbe1214finalhot6.common.idempotency;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class IdempotencyKeysTest {

    @Test
    @DisplayName("표준UUID는 그대로 반환한다")
    void 멱등키체크(){
        String key = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
        String result = IdempotencyKeys.validate(key);

        assertThat(result).isEqualTo(key);
    }
    @Test
    @DisplayName("null이면 COM-002")
    void 멱등키가널() {
        assertThatThrownBy(() -> IdempotencyKeys.validate(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_MISSING);
    }
    @Test
    @DisplayName("빈 문자열이면 COM-002")
    void 멱등키가빈문자열(){
        assertThatThrownBy(() -> IdempotencyKeys.validate(""))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_MISSING);
    }
    @Test
    @DisplayName("공백만 있으면 COM-002")
    void 멱등키가공백(){
        assertThatThrownBy(() -> IdempotencyKeys.validate("   "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_MISSING);
    }
    @Test
    @DisplayName("UUID 형식이 아니면 COM-001")
    void 멱등키가다른문자열(){
        assertThatThrownBy(() -> IdempotencyKeys.validate("abcdefg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }
    @Test
    @DisplayName("UUID 형식이 비표준 UUID인 경우 COM-001")
    void 멱등키가비표준형식(){
        assertThatThrownBy(() -> IdempotencyKeys.validate("1-1-1-1-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }
    @Test
    @DisplayName("UUID 형식이 대문자인 경우 통과")
    void 멱등키가대문자(){
        String key = "3F2504E0-4F89-11D3-9A0C-0305E82C3301";
        String result = IdempotencyKeys.validate(key);

        assertThat(result).isEqualTo(key);
    }
    @Test
    @DisplayName("UUID 형식에 앞뒤 공백이 포함된 경우 COM-001")//TODO : 이후 trim 시, 테스트 삭제
    void 멱등키앞뒤공백포함(){
        assertThatThrownBy(() -> IdempotencyKeys.validate(" 3f2504e0-4f89-11d3-9a0c-0305e82c3301 "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    }
}