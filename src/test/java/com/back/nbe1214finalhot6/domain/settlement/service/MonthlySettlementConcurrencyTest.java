package com.back.nbe1214finalhot6.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 여러 스레드가 각자 트랜잭션으로 커밋해야 하므로 @Transactional 을 붙이지 않고, 끝나면 직접 지운다.
@IntegrationTest
class MonthlySettlementConcurrencyTest {

  private static final int THREADS = 8;
  private static final Long OWNER = 8_201L;
  private static final long PAYOUT = 15_000L;

  @Autowired MonthlySettlementProcessor processor;
  @Autowired MonthlySettlementRepository monthlyRepository;
  @Autowired MerchantRepository merchantRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired AccountTransactionRepository accountTransactionRepository;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired MutableClock clock;

  private Long merchantId;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update(
        "delete from account_transaction where account_id in (select id from account where user_id = ?)",
        OWNER);
    jdbcTemplate.update("delete from account where user_id = ?", OWNER);
    monthlyRepository.deleteAll();
    if (merchantId != null) {
      merchantRepository.deleteById(merchantId);
    }
  }

  @Test
  @DisplayName("같은 월 정산 건의 지급을 동시에 처리해도 점주 계좌 입금은 한 번만 일어나고 지급 완료로 끝난다")
  void 동시지급_한번만() throws Exception {
    merchantId =
        merchantRepository.save(Merchant.create(OWNER, "동시 지급 가맹점", "인천광역시 남동구 1", true)).getId();
    accountRepository.save(Account.createEmpty(OWNER, LocalDateTime.now(clock)));
    MonthlySettlement settlement =
        monthlyRepository.save(
            MonthlySettlement.create(
                merchantId, LocalDate.of(2026, 10, 1), PAYOUT, LocalDateTime.now(clock)));
    Long settlementId = settlement.getId();

    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    CountDownLatch ready = new CountDownLatch(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        Callable<Boolean> task =
            () -> {
              ready.countDown();
              start.await();
              processor.payAndComplete(settlementId);
              return true;
            };
        futures.add(executor.submit(task));
      }
      ready.await();
      start.countDown();

      for (Future<Boolean> future : futures) {
        assertThat(future.get(10, TimeUnit.SECONDS)).isTrue();
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(monthlyRepository.findById(settlementId).orElseThrow().isPaid()).isTrue();
    assertThat(
            accountTransactionRepository.findByTypeAndRefKey(
                AccountTransactionType.SETTLEMENT_PAYOUT, settlementId))
        .hasValueSatisfying(tx -> assertThat(tx.getAmount()).isEqualTo(PAYOUT));
    assertThat(accountRepository.findByUserId(OWNER).orElseThrow().getBalance()).isEqualTo(PAYOUT);
  }
}
