package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.ReserveCommand;
import com.back.nbe1214finalhot6.domain.cashback.dto.ReserveResult;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** 다른 도메인이 주입받는 창구가 스프링 컨텍스트에 하나만 등록되어 있는지 확인한다. */
@IntegrationTest
class CashbackServiceWiringTest {

  @Autowired CashbackService cashbackService;
  @Autowired ApplicationContext context;

  @Test
  @DisplayName("CashbackService 는 하나만 등록되어 있고 reserve 를 호출할 수 있다")
  void 서비스주입() {
    assertThat(context.getBeansOfType(CashbackService.class)).hasSize(1);

    ReserveResult result =
        cashbackService.reserve(
            new ReserveCommand(1L, 1L, true, 12_000L, LocalDateTime.of(2026, 11, 18, 9, 0)));

    assertThat(result.earnedAmount()).isZero();
  }
}
