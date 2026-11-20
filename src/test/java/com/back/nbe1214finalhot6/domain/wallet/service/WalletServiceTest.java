package com.back.nbe1214finalhot6.domain.wallet.service;

import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.CASHBACK_CLAWBACK;
import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.CASHBACK_EARN;
import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.CHARGE;
import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.PAYMENT_CASHBACK;
import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.PAYMENT_DEVMONEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletTransactionRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class WalletServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);

  @Autowired WalletService walletService;
  @Autowired WalletRepository walletRepository;
  @Autowired WalletTransactionRepository walletTransactionRepository;
  @Autowired PlatformTransactionManager transactionManager;

  private Long walletId;

  @BeforeEach
  void setUp() {
    walletId = walletRepository.save(Wallet.create(1L, NOW)).getId();
  }

  // 테스트가 DB에 남긴 행 정리(원장 → 지갑 순)
  @AfterEach
  void cleanUp() {
    walletTransactionRepository.deleteAll();
    walletRepository.deleteAll();
  }

  @Test
  @DisplayName("입금하면 잔액이 늘고 원장에 양수로 기록된다")
  void credit_recordsPositiveLedger() {
    walletService.credit(walletId, 10_000L, CHARGE, null);

    assertThat(wallet().getBalance()).isEqualTo(10_000L);
    WalletTransaction ledger = ledgers(CHARGE).get(0);
    assertThat(ledger.getAmount()).isEqualTo(10_000L);
    assertThat(ledger.getBalanceAfter()).isEqualTo(10_000L);
  }

  @Test
  @DisplayName("차감하면 잔액이 줄고 원장에 음수로 기록된다")
  void debit_recordsNegativeLedger() {
    walletService.credit(walletId, 10_000L, CHARGE, null);

    walletService.debit(walletId, 3_000L, PAYMENT_DEVMONEY, 1L, false);

    assertThat(wallet().getBalance()).isEqualTo(7_000L);
    WalletTransaction ledger = ledgers(PAYMENT_DEVMONEY).get(0);
    assertThat(ledger.getAmount()).isEqualTo(-3_000L);
    assertThat(ledger.getBalanceAfter()).isEqualTo(7_000L);
  }

  @Test
  @DisplayName("캐시백 type은 캐시백 잔액만 바꾸고 데브머니는 그대로다")
  void cashbackType_changesOnlyCashback() {
    walletService.credit(walletId, 500L, CASHBACK_EARN, 1L);

    assertThat(wallet().getCashbackBalance()).isEqualTo(500L);
    assertThat(wallet().getBalance()).isZero();
  }

  @Test
  @DisplayName("같은 (type, refKey)를 다시 요청하면 한 번만 반영되고 예외가 없다")
  void duplicateRequest_appliedOnce() {
    walletService.credit(walletId, 500L, CASHBACK_EARN, 7L);
    walletService.credit(walletId, 500L, CASHBACK_EARN, 7L);

    assertThat(wallet().getCashbackBalance()).isEqualTo(500L);
    assertThat(ledgers(CASHBACK_EARN)).hasSize(1);
  }

  @Test
  @DisplayName("refKey가 같아도 type이 다르면 둘 다 반영된다(결제의 충전금·캐시백 두 번 호출)")
  void sameRefKeyDifferentType_bothApplied() {
    walletService.credit(walletId, 20_000L, CHARGE, null);
    walletService.credit(walletId, 10_000L, CASHBACK_EARN, 9L);

    walletService.debit(walletId, 2_000L, PAYMENT_DEVMONEY, 100L, false);
    walletService.debit(walletId, 1_000L, PAYMENT_CASHBACK, 100L, false);

    assertThat(wallet().getBalance()).isEqualTo(18_000L);
    assertThat(wallet().getCashbackBalance()).isEqualTo(9_000L);
    assertThat(ledgers(PAYMENT_DEVMONEY)).hasSize(1);
    assertThat(ledgers(PAYMENT_CASHBACK)).hasSize(1);
  }

  @Test
  @DisplayName("데브머니가 부족하면 WAL-004로 거절되고 잔액과 원장은 그대로다")
  void insufficientBalance() {
    walletService.credit(walletId, 1_000L, CHARGE, null);

    assertFails(
        () -> walletService.debit(walletId, 1_001L, PAYMENT_DEVMONEY, 1L, false),
        WalletErrorCode.INSUFFICIENT_BALANCE);

    assertThat(wallet().getBalance()).isEqualTo(1_000L);
    assertThat(ledgers(PAYMENT_DEVMONEY)).isEmpty();
  }

  @Test
  @DisplayName("캐시백 잔액이 0이면 결제의 캐시백 사용은 WAL-005로 거절된다")
  void cashbackZero_paymentRejected() {
    assertFails(
        () -> walletService.debit(walletId, 1L, PAYMENT_CASHBACK, 1L, false),
        WalletErrorCode.INSUFFICIENT_CASHBACK);
  }

  @Test
  @DisplayName("캐시백 회수는 음수를 허용하고, 이후 지급이 음수를 상계한다")
  void clawbackNegative_thenEarnOffsets() {
    walletService.credit(walletId, 100L, CASHBACK_EARN, 1L);

    walletService.debit(walletId, 300L, CASHBACK_CLAWBACK, 1L, true);
    assertThat(wallet().getCashbackBalance()).isEqualTo(-200L);

    walletService.credit(walletId, 150L, CASHBACK_EARN, 2L);
    assertThat(wallet().getCashbackBalance()).isEqualTo(-50L);
    // 데브머니와는 상계하지 않음
    assertThat(wallet().getBalance()).isZero();
  }

  @Test
  @DisplayName("방향이 다른 type으로 호출하면 거절된다")
  void wrongDirection_rejected() {
    assertThatThrownBy(() -> walletService.credit(walletId, 100L, PAYMENT_DEVMONEY, 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> walletService.debit(walletId, 100L, CHARGE, null, false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("0원은 WAL-003, 없는 지갑은 WAL-001로 거절된다")
  void invalidAmount_walletNotFound() {
    assertFails(
        () -> walletService.credit(walletId, 0L, CHARGE, null), WalletErrorCode.INVALID_AMOUNT);
    assertFails(
        () -> walletService.credit(walletId + 9_999L, 100L, CHARGE, null),
        WalletErrorCode.WALLET_NOT_FOUND);
  }

  @Test
  @DisplayName("탈퇴한 지갑은 입금과 차감 모두 WAL-002로 거절된다")
  void withdrawnWallet_rejected() {
    walletService.credit(walletId, 1_000L, CHARGE, null);
    Wallet wallet = wallet();
    wallet.withdraw(NOW);
    walletRepository.save(wallet);

    assertFails(
        () -> walletService.credit(walletId, 100L, CASHBACK_EARN, 1L),
        WalletErrorCode.WALLET_WITHDRAWN);
    assertFails(
        () -> walletService.debit(walletId, 100L, PAYMENT_DEVMONEY, 1L, false),
        WalletErrorCode.WALLET_WITHDRAWN);
  }

  @Test
  @DisplayName("같은 지갑에 동시에 차감해도 성공한 만큼만 차감되고 잔액은 음수가 되지 않는다")
  void concurrentDebit() throws Exception {
    walletService.credit(walletId, 5_000L, CHARGE, null);

    List<Throwable> errors =
        runConcurrently(
            10, i -> walletService.debit(walletId, 1_000L, PAYMENT_DEVMONEY, (long) i, false));

    // 10건 중 5건만 성공하고, 실패 5건은 모두 잔액 부족
    assertThat(errors).hasSize(5).allMatch(e -> e instanceof BusinessException);
    assertThat(wallet().getBalance()).isZero();
    assertThat(ledgers(PAYMENT_DEVMONEY)).hasSize(5);
  }

  @Test
  @DisplayName("같은 (type, refKey)가 동시에 와도 한 번만 반영되고 예외가 없다")
  void concurrentDuplicate_appliedOnce() throws Exception {
    List<Throwable> errors =
        runConcurrently(5, i -> walletService.credit(walletId, 100L, CASHBACK_EARN, 77L));

    assertThat(errors).isEmpty();
    assertThat(wallet().getCashbackBalance()).isEqualTo(100L);
    assertThat(ledgers(CASHBACK_EARN)).hasSize(1);
  }

  @Test
  @DisplayName("바깥 트랜잭션이 롤백되면 입금도 함께 롤백된다")
  void joinsOuterTransaction() {
    new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              walletService.credit(walletId, 10_000L, CHARGE, null);
              status.setRollbackOnly();
              return null;
            });

    assertThat(wallet().getBalance()).isZero();
    assertThat(ledgers()).isEmpty();
  }

  private Wallet wallet() {
    return walletRepository.findById(walletId).orElseThrow();
  }

  // 이 테스트의 지갑에 쌓인 원장만 조회(다른 데이터에 영향받지 않도록)
  private List<WalletTransaction> ledgers() {
    return walletTransactionRepository.findAll().stream()
        .filter(t -> t.getWalletId().equals(walletId))
        .toList();
  }

  private List<WalletTransaction> ledgers(WalletTransactionType type) {
    return ledgers().stream().filter(t -> t.getType() == type).toList();
  }

  private void assertFails(ThrowingCallable call, WalletErrorCode expected) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }

  // count개의 스레드를 동시에 출발시켜 작업을 실행하고, 작업 중 발생한 예외를 모아서 돌려줌
  private List<Throwable> runConcurrently(int count, IntConsumer task) throws InterruptedException {
    List<Throwable> errors = new CopyOnWriteArrayList<>();
    ExecutorService pool = Executors.newFixedThreadPool(count);
    CountDownLatch ready = new CountDownLatch(count);
    CountDownLatch start = new CountDownLatch(1);
    for (int i = 0; i < count; i++) {
      int index = i;
      pool.execute(
          () -> {
            ready.countDown();
            try {
              start.await();
              task.accept(index);
            } catch (Throwable e) {
              errors.add(e);
            }
          });
    }
    ready.await();
    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    return errors;
  }
}
