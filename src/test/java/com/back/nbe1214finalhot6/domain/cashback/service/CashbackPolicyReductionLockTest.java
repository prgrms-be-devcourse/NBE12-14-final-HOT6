package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackNoticeRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyHistoryRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 같은 달에 요율 인하가 동시에 들어와도 한 달 인하 횟수(기본 1회)를 지키는지 확인한다.
 *
 * <p>실제 커밋이 오가야 하므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class CashbackPolicyReductionLockTest {

  private static final long OPERATOR_ID = 99L;

  private static final LocalDateTime NOV_20 = LocalDateTime.of(2026, 11, 20, 0, 0);

  @Autowired private CashbackPolicyService policyService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackPolicyRepository policyRepository;
  @Autowired private CashbackPolicyHistoryRepository historyRepository;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  @Autowired private CashbackNoticeRepository noticeRepository;
  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;

  @AfterEach
  void cleanUp() {
    historyRepository.deleteAll();
    policyRepository.deleteAll();
    noticeRepository.deleteAll(); // 상태 전이·요율 인하가 남긴 공지가 다른 테스트에 섞이지 않게 한다
    monthlyBudgetRepository.deleteAll();
    annualBudgetRepository.deleteAll();
  }

  // 목적: 같은 달에 두 인하가 동시에 들어오면 월 예산 행 잠금으로 직렬화되어, 한 건만 성공하고 한 건은 CSH-009 로
  // 거부되는지 확인한다. 잠금이 없으면 둘 다 횟수 0 을 보고 통과해 구간이 겹치거나 두 번 인하된다.
  @Test
  @DisplayName("같은 달에 동시에 인하하면 한 건만 성공하고 나머지는 CSH-009")
  void concurrentReductions_onlyOneSucceeds() throws Exception {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(
        2026, 11, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.05")));
    int threads = 4;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<String>> results = new ArrayList<>();
    try {
      for (int i = 0; i < threads; i++) {
        // 스레드마다 다른 요율(0.04, 0.039, ...)로 요청한다
        BigDecimal rate =
            new BigDecimal("0.04").subtract(new BigDecimal("0.001").multiply(BigDecimal.valueOf(i)));
        results.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  try {
                    policyService.reduceRate(new RateReductionRequest(rate, NOV_20, "테스트 사유"), OPERATOR_ID);
                    return "OK";
                  } catch (BusinessException e) {
                    return e.getErrorCode().getCode();
                  }
                }));
      }
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      go.countDown();

      List<String> outcomes = new ArrayList<>();
      for (Future<String> result : results) {
        outcomes.add(result.get(10, TimeUnit.SECONDS));
      }
      assertThat(outcomes.stream().filter("OK"::equals).count()).isEqualTo(1);
      String limitCode = CashbackErrorCode.RATE_REDUCTION_LIMIT_EXCEEDED.getCode();
      assertThat(outcomes.stream().filter(limitCode::equals).count()).isEqualTo(threads - 1);
    } finally {
      executor.shutdownNow();
    }

    assertThat(policyRepository.count()).isEqualTo(1);
    assertThat(historyRepository.count()).isEqualTo(1);
  }
}
