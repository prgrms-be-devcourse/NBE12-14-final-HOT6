package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyLimit;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyLimitRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 이용자 월 적립 한도 사용·복원. */
@IntegrationTest
@Transactional
class CashbackMonthlyLimitServiceTest {

  private static final long LIMIT = 50_000L;
  private static final YearMonth NOV = YearMonth.of(2026, 11);
  private static final YearMonth DEC = YearMonth.of(2026, 12);

  @Autowired private CashbackMonthlyLimitService limitService;
  @Autowired private CashbackMonthlyLimitRepository limitRepository;
  @PersistenceContext private EntityManager entityManager;

  private void assertInvalid(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_BUDGET);
  }

  // 목적: 그 달 행이 없어도 첫 사용이 성공하고 사용량이 쌓이는지, 사용량이 값으로 조회되는지 확인한다.
  @Test
  @DisplayName("첫 사용은 행을 만들고 이후 사용은 사용량을 더한다")
  void consume_accumulates() {
    assertThat(limitService.usedAmount(1L, NOV)).isZero();

    assertThat(limitService.consume(1L, NOV, 12_000L, LIMIT)).isTrue();
    assertThat(limitService.consume(1L, NOV, 8_000L, LIMIT)).isTrue();

    assertThat(limitService.usedAmount(1L, NOV)).isEqualTo(20_000L);
    assertThat(limitRepository.count()).isEqualTo(1);
  }

  // 목적: 딱 한도까지 사용할 수 있고(경계), 1원이라도 넘으면 실패하며 사용량이 그대로인지 확인한다. 부분 사용은 없다.
  @Test
  @DisplayName("딱 한도까지는 사용하고 1원 넘으면 실패하며 사용량은 그대로다")
  void consume_boundary() {
    limitService.consume(1L, NOV, 30_000L, LIMIT);

    assertThat(limitService.consume(1L, NOV, 20_001L, LIMIT)).isFalse();
    assertThat(limitService.usedAmount(1L, NOV)).isEqualTo(30_000L);

    assertThat(limitService.consume(1L, NOV, 20_000L, LIMIT)).isTrue();
    assertThat(limitService.usedAmount(1L, NOV)).isEqualTo(LIMIT);
    assertThat(limitService.consume(1L, NOV, 1L, LIMIT)).isFalse();
  }

  // 목적: 한 번에 한도를 넘는 금액은 새 이용자·새 달이어도 실패하고, 사용량은 0 으로 남는지 확인한다(행은 생겨도 쓰인 것은 없다).
  @Test
  @DisplayName("처음부터 한도를 넘는 금액은 실패하고 사용량은 0 이다")
  void consume_overLimitOnFirstUse() {
    assertThat(limitService.consume(1L, NOV, LIMIT + 1, LIMIT)).isFalse();

    assertThat(limitService.usedAmount(1L, NOV)).isZero();
  }

  // 목적: 운영자가 한도를 이미 쓴 양보다 낮추면(한도 < 사용량) 이후 사용이 막히는지 확인한다. 회수는 하지 않는다.
  @Test
  @DisplayName("한도를 사용량보다 낮추면 이후 사용이 막힌다")
  void consume_limitLoweredBelowUsed() {
    limitService.consume(1L, NOV, 40_000L, LIMIT);

    assertThat(limitService.consume(1L, NOV, 1L, 30_000L)).isFalse();
    assertThat(limitService.usedAmount(1L, NOV)).isEqualTo(40_000L);
    // 한도를 다시 올리면 그만큼 쓸 수 있다
    assertThat(limitService.consume(1L, NOV, 10_000L, LIMIT)).isTrue();
  }

  // 목적: 월과 이용자가 다르면 사용량이 따로 쌓이는지 확인한다(새 달에는 0 에서 시작).
  @Test
  @DisplayName("달과 이용자가 다르면 사용량이 따로 쌓인다")
  void consume_separatedByMonthAndWallet() {
    limitService.consume(1L, NOV, 50_000L, LIMIT);

    assertThat(limitService.consume(1L, DEC, 50_000L, LIMIT)).isTrue(); // 새 달
    assertThat(limitService.consume(2L, NOV, 50_000L, LIMIT)).isTrue(); // 다른 이용자
    assertThat(limitService.consume(1L, NOV, 1L, LIMIT)).isFalse();
    assertThat(limitRepository.count()).isEqualTo(3);
  }

  // 목적: 아주 큰 한도에서도 합이 넘쳐서 잘못 통과하지 않는지 확인한다(합이 아니라 한도 - 사용량과 비교).
  @Test
  @DisplayName("아주 큰 값에서도 넘치지 않고 정확히 비교한다")
  void consume_noOverflow() {
    limitService.consume(1L, NOV, 1_000L, Long.MAX_VALUE);

    assertThat(limitService.consume(1L, NOV, Long.MAX_VALUE, Long.MAX_VALUE)).isFalse();
    assertThat(limitService.usedAmount(1L, NOV)).isEqualTo(1_000L);
  }

  // 목적: 금액이 0 이하이면 사용·복원 모두 CSH-007 로 거부되고 아무것도 바뀌지 않는지 확인한다.
  @Test
  @DisplayName("금액이 0 이하이면 거부한다")
  void invalidAmount() {
    assertInvalid(() -> limitService.consume(1L, NOV, 0L, LIMIT));
    assertInvalid(() -> limitService.consume(1L, NOV, -5L, LIMIT));
    assertInvalid(() -> limitService.release(1L, NOV, 0L));

    assertThat(limitRepository.count()).isZero();
  }

  // 목적: 복원하면 사용량이 줄고 복원한 만큼 다시 사용할 수 있는지 확인한다.
  @Test
  @DisplayName("복원하면 사용량이 줄고 다시 사용할 수 있다")
  void release_restores() {
    limitService.consume(1L, NOV, LIMIT, LIMIT);

    limitService.release(1L, NOV, 15_000L);

    assertThat(limitService.usedAmount(1L, NOV)).isEqualTo(35_000L);
    assertThat(limitService.consume(1L, NOV, 15_000L, LIMIT)).isTrue();
  }

  // 목적: 사용량보다 많이 복원하거나 사용한 적 없는 달을 복원하려 하면 CSH-007 로 거부되는지 확인한다. 취소는 결제한 달에서만 되돌린다.
  @Test
  @DisplayName("사용량보다 많이 복원하거나 사용 안 한 달을 복원하면 거부한다")
  void release_overRelease() {
    limitService.consume(1L, NOV, 10_000L, LIMIT);

    assertInvalid(() -> limitService.release(1L, NOV, 10_001L));
    assertInvalid(() -> limitService.release(1L, DEC, 1L)); // 결제하지 않은 달
    assertInvalid(() -> limitService.release(2L, NOV, 1L)); // 다른 이용자
    assertThat(limitService.usedAmount(1L, NOV)).isEqualTo(10_000L);
  }

  // 목적: 사용·복원이 영속성 컨텍스트를 비우지 않아서, 호출한 쪽(결제 트랜잭션)이 먼저 읽어 둔 엔티티가 분리되지 않는지 확인한다.
  @Test
  @DisplayName("사용·복원해도 호출한 쪽이 읽어 둔 엔티티는 영속 상태로 남는다")
  void keepsPersistenceContext() {
    limitService.consume(1L, NOV, 10_000L, LIMIT);
    entityManager.flush();
    entityManager.clear();
    CashbackMonthlyLimit loaded =
        limitRepository.findAll().stream().findFirst().orElseThrow();

    limitService.consume(1L, NOV, 5_000L, LIMIT);
    limitService.release(1L, NOV, 5_000L);

    assertThat(entityManager.contains(loaded)).isTrue();
  }
}
