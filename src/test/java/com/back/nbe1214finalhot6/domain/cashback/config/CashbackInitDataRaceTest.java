package com.back.nbe1214finalhot6.domain.cashback.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import java.math.BigDecimal;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** 연 예산 자동 등록이 무결성 오류를 만났을 때의 처리를 확인한다. */
class CashbackInitDataRaceTest {

  private final CashbackBudgetService budgetService = mock(CashbackBudgetService.class);
  private final CashbackAnnualBudgetRepository repository =
      mock(CashbackAnnualBudgetRepository.class);

  private CashbackInitData initData() {
    CashbackProperties properties =
        new CashbackProperties(
            new BigDecimal("0.10"),
            new BigDecimal("0.5"),
            3,
            3,
            0,
            1,
            null,
            null,
            new CashbackProperties.Init(true, 100_000_000L));
    given(budgetService.createAnnualIfAbsent(anyInt(), anyLong()))
        .willThrow(new DataIntegrityViolationException("violation"));
    return new CashbackInitData(properties, repository, budgetService, Clock.systemDefaultZone());
  }

  // 목적: 다른 쪽이 먼저 만들어 실패한 것이면 서버 시작을 막지 않는지 확인한다.
  @Test
  @DisplayName("다른 쪽이 먼저 만들었으면 넘어간다")
  void ignoresWhenAlreadyCreated() {
    given(repository.existsByBudgetYear(anyInt())).willReturn(true);

    initData().run(null);
  }

  // 목적: 행이 없는데 난 무결성 오류는 숨기지 않는지 확인한다.
  @Test
  @DisplayName("행이 없으면 오류를 그대로 던진다")
  void rethrowsWhenStillMissing() {
    given(repository.existsByBudgetYear(anyInt())).willReturn(false);

    CashbackInitData initData = initData();

    assertThatThrownBy(() -> initData.run(null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
