package com.back.nbe1214finalhot6.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantClosureSettlementProcessor.Outcome;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.MerchantWalletService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 여러 스레드가 각자 트랜잭션으로 커밋해야 하므로 @Transactional 을 붙이지 않고, 끝나면 직접 지운다.
@IntegrationTest
class MerchantClosureSettlementConcurrencyTest {

  private static final int THREADS = 8;
  private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

  @Autowired private MerchantClosureSettlementProcessor processor;
  @Autowired private MerchantRepository merchantRepository;
  @Autowired private MerchantWalletService merchantWalletService;
  @Autowired private MerchantAccountRepository accountRepository;
  @Autowired private DailySettlementRepository dailySettlementRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("같은 가맹점의 해지 정산을 동시에 실행해도 한 번만 지급되고 나머지는 이미 처리됨이다")
  void 동시에_실행해도_한번만_지급() throws Exception {
    Long merchantId =
        merchantRepository
            .save(Merchant.create(9_501L, "동시 해지 정산 가맹점", "인천광역시 남동구 1", true))
            .getId();
    merchantWalletService.createFor(merchantId);
    // 수정 쿼리는 트랜잭션이 필요해서, 이 테스트에는 트랜잭션이 없으므로 직접 열어서 호출한다
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status ->
                merchantRepository.updateStatusIfCurrent(
                    merchantId, MerchantStatus.ACTIVE, MerchantStatus.CLOSING));
    dailySettlementRepository.save(
        DailySettlement.create(merchantId, DAY, 30_000, 0, 0, LocalDateTime.now()));
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    CountDownLatch ready = new CountDownLatch(THREADS);
    CountDownLatch start = new CountDownLatch(1);

    try {
      List<Future<Outcome>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        Callable<Outcome> task =
            () -> {
              ready.countDown();
              start.await();
              return processor.settleOne(merchantId, DAY);
            };
        futures.add(executor.submit(task));
      }
      ready.await();
      start.countDown();

      int closed = 0;
      int already = 0;
      for (Future<Outcome> future : futures) {
        Outcome outcome = future.get(15, TimeUnit.SECONDS);
        if (outcome == Outcome.CLOSED) {
          closed++;
        } else if (outcome == Outcome.ALREADY) {
          already++;
        }
      }

      assertThat(closed).isEqualTo(1);
      assertThat(already).isEqualTo(THREADS - 1);
      assertThat(accountRepository.findByMerchantId(merchantId).orElseThrow().getBalance())
          .isEqualTo(30_000);
      assertThat(merchantRepository.findStatusById(merchantId)).contains(MerchantStatus.CLOSED);
    } finally {
      executor.shutdownNow();
      jdbcTemplate.update(
          "delete from merchant_wallet_transaction where wallet_id in "
              + "(select id from merchant_wallet where merchant_id = ?)",
          merchantId);
      jdbcTemplate.update("delete from merchant_wallet where merchant_id = ?", merchantId);
      jdbcTemplate.update("delete from merchant_account where merchant_id = ?", merchantId);
      jdbcTemplate.update("delete from merchant_closure_deduction where merchant_id = ?", merchantId);
      jdbcTemplate.update("delete from monthly_settlement where merchant_id = ?", merchantId);
      jdbcTemplate.update("delete from daily_settlement where merchant_id = ?", merchantId);
      jdbcTemplate.update("delete from merchant where id = ?", merchantId);
    }
  }
}
