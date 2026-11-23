package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 적립 취소(지급 전). 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class CashbackServiceCancelTest {

  private static final long WALLET = 7L;
  private static final YearMonth NOV = YearMonth.of(2026, 11);
  private static final YearMonth DEC = YearMonth.of(2026, 12);
  private static final LocalDateTime NOV_AT = LocalDateTime.of(2026, 11, 18, 9, 0);
  private static final LocalDateTime DEC_AT = LocalDateTime.of(2026, 12, 1, 10, 0);

  @Autowired private CashbackService service;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackMonthlyLimitService limitService;
  @Autowired private CashbackEarningRepository earningRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Clock clock;
  @PersistenceContext private EntityManager entityManager;

  // 연 예산 12,000,000 / 11·12월 배정 각 1,000,000, 기본 요율 3%
  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
    budgetService.putMonthly(2026, 12, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  private void reserve(long paymentId, long walletUsed, LocalDateTime at) {
    service.reserve(new CashbackReserveRequest(paymentId, WALLET, true, walletUsed, at));
  }

  private long annualReserved() {
    entityManager.flush();
    entityManager.clear();
    return budgetService.getAnnual(2026).reservedAmount();
  }

  private long monthlyReserved(int month) {
    entityManager.flush();
    entityManager.clear();
    return budgetService.getMonthly(2026, month).reservedAmount();
  }

  private CashbackEarning earning(long paymentId) {
    entityManager.flush();
    entityManager.clear();
    return earningRepository.findByPaymentId(paymentId).orElseThrow();
  }

  // 목적: 취소하면 적립 건이 CANCELED 가 되고 연·월 예산과 월 한도가 확보 전 상태로 돌아오는지 확인한다.
  @Test
  @DisplayName("취소하면 적립 건이 취소되고 예산과 월 한도가 원래대로 돌아온다")
  void cancel_restoresEverything() {
    reserve(1L, 12_000L, NOV_AT);
    assertThat(limitService.usedAmount(WALLET, NOV)).isEqualTo(360L);

    service.cancel(1L);

    CashbackEarning result = earning(1L);
    assertThat(result.getStatus()).isEqualTo(EarningStatus.CANCELED);
    assertThat(result.getCanceledAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 0));
    assertThat(limitService.usedAmount(WALLET, NOV)).isZero();
    assertThat(monthlyReserved(11)).isZero();
    assertThat(annualReserved()).isZero();
  }

  // 목적: 같은 결제를 두 번 취소해도 한 번만 되돌리는지 확인한다. 같은 트랜잭션에서 다시 부르면 컨텍스트의 낡은 엔티티(PENDING)를 읽게 되므로 그
  // 경로도 함께 확인한다.
  @Test
  @DisplayName("같은 결제를 여러 번 취소해도 한 번만 되돌린다")
  void cancel_isIdempotent() {
    reserve(1L, 12_000L, NOV_AT);
    reserve(2L, 10_000L, NOV_AT);

    service.cancel(1L);
    service.cancel(1L);
    entityManager.flush();
    entityManager.clear();
    service.cancel(1L);

    assertThat(limitService.usedAmount(WALLET, NOV)).isEqualTo(300L);
    assertThat(monthlyReserved(11)).isEqualTo(300L);
    assertThat(annualReserved()).isEqualTo(300L);
  }

  // 목적: 적립 건이 없거나 제외 건인 결제의 취소는 예외도 변화도 없는지 확인한다.
  @Test
  @DisplayName("적립 건이 없거나 제외 건이면 아무것도 하지 않는다")
  void cancel_noopForMissingAndExcluded() {
    reserve(1L, 12_000L, NOV_AT);
    service.reserve(new CashbackReserveRequest(2L, WALLET, false, 12_000L, NOV_AT));

    assertThatCode(
            () -> {
              service.cancel(999L);
              service.cancel(2L);
            })
        .doesNotThrowAnyException();

    assertThat(earning(2L).getStatus()).isEqualTo(EarningStatus.EXCLUDED);
    assertThat(limitService.usedAmount(WALLET, NOV)).isEqualTo(360L);
    assertThat(monthlyReserved(11)).isEqualTo(360L);
  }

  // 목적: 지난달 결제를 달이 바뀐 뒤 취소해도 그 달의 한도·예산에서만 되돌리고 이번 달 한도는 건드리지 않는지 확인한다.
  @Test
  @DisplayName("지난달 결제를 취소해도 이번 달 한도와 예산은 늘지 않는다")
  void cancel_restoresPaymentMonthOnly() {
    reserve(1L, 12_000L, NOV_AT);
    reserve(2L, 10_000L, DEC_AT);
    ((MutableClock) clock).advance(Duration.ofDays(20));

    service.cancel(1L);

    assertThat(limitService.usedAmount(WALLET, NOV)).isZero();
    assertThat(limitService.usedAmount(WALLET, DEC)).isEqualTo(300L);
    assertThat(monthlyReserved(11)).isZero();
    assertThat(monthlyReserved(12)).isEqualTo(300L);
  }

  // 목적: 이미 지급된 적립은 지급 후 회수가 구현되기 전까지 명시적으로 거절되고 아무것도 되돌리지 않는지 확인한다.
  @Test
  @DisplayName("이미 지급된 적립은 거절하고 예산과 한도를 그대로 둔다")
  void cancel_rejectsPaid() {
    reserve(1L, 12_000L, NOV_AT);
    jdbcTemplate.update("update cashback_earning set status = 'PAID' where payment_id = 1");
    entityManager.clear();

    assertThatThrownBy(() -> service.cancel(1L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.EARNING_ALREADY_PAID);

    assertThat(limitService.usedAmount(WALLET, NOV)).isEqualTo(360L);
    assertThat(monthlyReserved(11)).isEqualTo(360L);
  }

  // 목적: 읽어 둔 뒤에 지급으로 바뀐 경우(낡은 엔티티는 PENDING)에도 조건부 전환이 0건이라 거절되는지 확인한다.
  @Test
  @DisplayName("읽은 뒤 지급으로 바뀌었어도 취소하지 않고 거절한다")
  void cancel_rejectsPaidAfterRead() {
    reserve(1L, 12_000L, NOV_AT);
    entityManager.flush();
    jdbcTemplate.update("update cashback_earning set status = 'PAID' where payment_id = 1");
    // 컨텍스트를 비우지 않아 낡은 PENDING 엔티티가 남아 있다

    assertThatThrownBy(() -> service.cancel(1L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.EARNING_ALREADY_PAID);
    assertThat(limitService.usedAmount(WALLET, NOV)).isEqualTo(360L);
  }
}
