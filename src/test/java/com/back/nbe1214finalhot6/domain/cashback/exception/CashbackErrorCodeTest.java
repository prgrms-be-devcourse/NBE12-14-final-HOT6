package com.back.nbe1214finalhot6.domain.cashback.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CashbackErrorCodeTest {

  @Test
  @DisplayName("에러 코드는 CSH-숫자3자리 형식이고 서로 겹치지 않는다")
  void 코드형식과중복() {
    List<String> codes =
        Arrays.stream(CashbackErrorCode.values()).map(CashbackErrorCode::getCode).toList();

    assertThat(codes).allMatch(code -> code.matches("CSH-\\d{3}")).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("모든 에러 코드는 상태와 메시지를 가진다")
  void 상태와메시지() {
    for (CashbackErrorCode errorCode : CashbackErrorCode.values()) {
      assertThat(errorCode.getStatus()).isNotNull();
      assertThat(errorCode.getMessage()).isNotBlank();
    }
  }
}
