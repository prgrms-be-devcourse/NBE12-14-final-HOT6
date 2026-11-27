package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.AdminEarningResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 운영자의 적립 내역 조회. */
@IntegrationTest
@Transactional
class CashbackEarningAdminServiceTest {

  private static final long WALLET = 7L;
  private static final long OTHER = 8L;
  private static final LocalDate NOV_10 = LocalDate.of(2026, 11, 10);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 9, 0);

  @Autowired private CashbackEarningAdminService service;
  @Autowired private CashbackEarningRepository earningRepository;

  // 결제 1: 7번 지갑, 11-09 끝, 예정 / 결제 2: 7번, 11-10 0시, 제외 / 결제 3: 8번, 11-10 끝, 예정 / 결제 4: 8번, 11-11 0시, 예정
  @BeforeEach
  void setUp() {
    pending(1L, WALLET, NOV_10.minusDays(1).atTime(23, 59, 59));
    earningRepository.save(
        CashbackEarning.excluded(
            2L,
            WALLET,
            10_000L,
            NOV_10.atStartOfDay(),
            CashbackExclusionReason.MONTHLY_LIMIT_EXCEEDED,
            NOW));
    pending(3L, OTHER, NOV_10.atTime(23, 59, 59));
    pending(4L, OTHER, NOV_10.plusDays(1).atStartOfDay());
  }

  private void pending(long paymentId, long walletId, LocalDateTime requestedAt) {
    earningRepository.save(
        CashbackEarning.pending(
            paymentId,
            walletId,
            10_000L,
            requestedAt,
            new BigDecimal("0.03"),
            null,
            300L,
            requestedAt.toLocalDate().plusDays(4).atStartOfDay(),
            LocalDate.of(2026, 11, 1),
            NOW));
  }

  private PageResponse<AdminEarningResponse> search(
      EarningStatus status, Long walletId, LocalDate from, LocalDate to) {
    return service.search(status, walletId, from, to, 0, 20);
  }

  // 목적: 조건이 없으면 모든 지갑의 건이 최근 접수순으로 오고, 지갑 번호와 기준 금액이 담기는지 확인한다.
  @Test
  @DisplayName("조건이 없으면 전체를 최근 접수순으로 준다")
  void all() {
    assertThat(search(null, null, null, null).content())
        .extracting(AdminEarningResponse::paymentId)
        .containsExactly(4L, 3L, 2L, 1L);
    assertThat(search(null, WALLET, null, null).content().get(0))
        .satisfies(
            e -> {
              assertThat(e.walletId()).isEqualTo(WALLET);
              assertThat(e.walletUsedAmount()).isEqualTo(10_000L);
              assertThat(e.exclusionReason())
                  .isEqualTo(CashbackExclusionReason.MONTHLY_LIMIT_EXCEEDED);
            });
  }

  // 목적: 기간은 시작일 0시부터 종료일 끝까지이며, 한쪽만 줄 수도 있는지 확인한다.
  @Test
  @DisplayName("기간은 양 끝 날짜를 포함한다")
  void period() {
    assertThat(search(null, null, NOV_10, NOV_10).content())
        .extracting(AdminEarningResponse::paymentId)
        .containsExactly(3L, 2L);
    assertThat(search(null, null, NOV_10, null).totalElements()).isEqualTo(3);
    assertThat(search(null, null, null, NOV_10).totalElements()).isEqualTo(3);
  }

  // 목적: 상태, 지갑, 기간을 함께 주면 모두 만족하는 건만 오는지 확인한다.
  @Test
  @DisplayName("상태·지갑·기간을 함께 거른다")
  void combined() {
    assertThat(search(EarningStatus.PENDING, OTHER, NOV_10, NOV_10).content())
        .extracting(AdminEarningResponse::paymentId)
        .containsExactly(3L);
    assertThat(search(EarningStatus.PAID, null, null, null).content()).isEmpty();
  }

  // 목적: 페이지로 나누고, 너무 큰 크기는 상한으로 줄이는지 확인한다.
  @Test
  @DisplayName("페이지로 나누고 크기는 100 까지다")
  void paging() {
    PageResponse<AdminEarningResponse> second = service.search(null, null, null, null, 1, 3);

    assertThat(second.content()).extracting(AdminEarningResponse::paymentId).containsExactly(1L);
    assertThat(second.totalPages()).isEqualTo(2);
    assertThat(service.search(null, null, null, null, 0, 1_000).size()).isEqualTo(100);
  }

  // 목적: 시작일이 종료일보다 늦으면 CSH-016 인지 확인한다.
  @Test
  @DisplayName("시작일이 종료일보다 늦으면 거절한다")
  void invalidPeriod() {
    assertThatThrownBy(() -> search(null, null, NOV_10.plusDays(1), NOV_10))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CashbackErrorCode.INVALID_SEARCH_PERIOD);
  }
}
