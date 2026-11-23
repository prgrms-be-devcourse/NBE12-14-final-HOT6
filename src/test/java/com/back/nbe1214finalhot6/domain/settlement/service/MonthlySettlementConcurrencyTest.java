package com.back.nbe1214finalhot6.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.MerchantWalletService;
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

// 여러 스레드가 각자 트랜잭션으로 커밋해야 하므로 @Transactional 을 붙이지 않고, 끝나면 직접 지운다.
@IntegrationTest
class MonthlySettlementConcurrencyTest {

  private static final int THREADS = 8;
  private static final Long MERCHANT = 1L;
  private static final long PAYOUT = 15_000L;

  @Autowired MonthlySettlementProcessor processor;
  @Autowired MonthlySettlementRepository monthlyRepository;
  @Autowired MerchantWalletService walletService;
  @Autowired MerchantWalletRepository walletRepository;
  @Autowired MerchantAccountRepository accountRepository;
  @Autowired MerchantWalletTransactionRepository transactionRepository;
  @Autowired MutableClock clock;

  @AfterEach
  void cleanUp() {
    transactionRepository.deleteAll();
    monthlyRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
  }

  @Test
  @DisplayName("같은 월 정산 건의 출금을 동시에 처리해도 출금은 한 번만 일어나고 지급 완료로 끝난다")
  void 동시출금_한번만() throws Exception {
    walletService.createFor(MERCHANT);
    MonthlySettlement settlement =
        monthlyRepository.save(
            MonthlySettlement.create(
                MERCHANT, LocalDate.of(2026, 10, 1), PAYOUT, LocalDateTime.now(clock)));
    Long settlementId = settlement.getId();
    walletService.deposit(MERCHANT, PAYOUT, settlementId);

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
              processor.withdrawAndComplete(settlementId);
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
            transactionRepository.findByTypeAndRefKey(
                MerchantWalletTransactionType.PAYOUT_TO_ACCOUNT, settlementId))
        .isPresent();
    assertThat(transactionRepository.count()).isEqualTo(2);
    assertThat(walletRepository.findByMerchantId(MERCHANT).orElseThrow().getBalance()).isZero();
    assertThat(accountRepository.findByMerchantId(MERCHANT).orElseThrow().getBalance())
        .isEqualTo(PAYOUT);
  }
}
