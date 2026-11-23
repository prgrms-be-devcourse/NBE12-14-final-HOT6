package com.back.nbe1214finalhot6.domain.wallet.service;

import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.CASHBACK_CLAWBACK;
import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.CASHBACK_EARN;
import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.CHARGE;
import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.PAYMENT_CANCEL_CASHBACK;
import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.PAYMENT_CANCEL_DEVMONEY;
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
import org.springframework.transaction.TransactionDefinition;
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

  @Test
  @DisplayName("결제 차감은 충전금과 캐시백을 한 번에 차감하고 원장에 두 줄을 남긴다")
  void debitPayment_bothBuckets() {
    fund(50_000L, 10_000L);

    walletService.debitPayment(walletId, 20_000L, 10_000L, 100L);

    assertThat(wallet().getBalance()).isEqualTo(30_000L);
    assertThat(wallet().getCashbackBalance()).isZero();
    WalletTransaction devMoney = ledgers(PAYMENT_DEVMONEY).get(0);
    assertThat(devMoney.getAmount()).isEqualTo(-20_000L);
    assertThat(devMoney.getBalanceAfter()).isEqualTo(30_000L);
    assertThat(devMoney.getRefKey()).isEqualTo(100L);
    WalletTransaction cashback = ledgers(PAYMENT_CASHBACK).get(0);
    assertThat(cashback.getAmount()).isEqualTo(-10_000L);
    assertThat(cashback.getBalanceAfter()).isZero();
    assertThat(cashback.getRefKey()).isEqualTo(100L);
  }

  @Test
  @DisplayName("결제 차감에서 0원인 쪽은 원장을 만들지 않는다")
  void debitPayment_zeroSideSkipped() {
    fund(50_000L, 10_000L);

    walletService.debitPayment(walletId, 30_000L, 0L, 101L);

    assertThat(wallet().getBalance()).isEqualTo(20_000L);
    assertThat(wallet().getCashbackBalance()).isEqualTo(10_000L);
    assertThat(ledgers(PAYMENT_DEVMONEY)).hasSize(1);
    assertThat(ledgers(PAYMENT_CASHBACK)).isEmpty();
  }

  @Test
  @DisplayName("결제 차감과 환급은 음수이거나 둘 다 0원이면 WAL-003으로 거절된다")
  void paymentAmounts_invalid() {
    fund(50_000L, 10_000L);

    assertFails(
        () -> walletService.debitPayment(walletId, 0L, 0L, 102L), WalletErrorCode.INVALID_AMOUNT);
    assertFails(
        () -> walletService.debitPayment(walletId, -1L, 100L, 102L),
        WalletErrorCode.INVALID_AMOUNT);
    assertFails(
        () -> walletService.creditPaymentCancel(walletId, 100L, -1L, 102L),
        WalletErrorCode.INVALID_AMOUNT);

    assertThat(wallet().getBalance()).isEqualTo(50_000L);
    assertThat(wallet().getCashbackBalance()).isEqualTo(10_000L);
  }

  @Test
  @DisplayName("같은 결제 ID로 결제 차감을 다시 요청하면 한 번만 반영된다")
  void debitPayment_duplicateAppliedOnce() {
    fund(50_000L, 10_000L);

    walletService.debitPayment(walletId, 20_000L, 10_000L, 103L);
    walletService.debitPayment(walletId, 20_000L, 10_000L, 103L);

    assertThat(wallet().getBalance()).isEqualTo(30_000L);
    assertThat(wallet().getCashbackBalance()).isZero();
    assertThat(ledgers(PAYMENT_DEVMONEY)).hasSize(1);
    assertThat(ledgers(PAYMENT_CASHBACK)).hasSize(1);
  }

  @Test
  @DisplayName("캐시백이 부족하면 WAL-005로 거절되고 먼저 처리한 충전금 차감도 취소된다")
  void debitPayment_cashbackShortage_rollsBackDevMoney() {
    fund(50_000L, 500L);

    assertFails(
        () -> walletService.debitPayment(walletId, 20_000L, 501L, 104L),
        WalletErrorCode.INSUFFICIENT_CASHBACK);

    assertThat(wallet().getBalance()).isEqualTo(50_000L);
    assertThat(wallet().getCashbackBalance()).isEqualTo(500L);
    assertThat(ledgers(PAYMENT_DEVMONEY)).isEmpty();
    assertThat(ledgers(PAYMENT_CASHBACK)).isEmpty();
  }

  @Test
  @DisplayName("결제 취소 환급은 낸 칸 그대로 돌려주고, 다시 요청해도 한 번만 반영된다")
  void creditPaymentCancel_restoresAndAppliedOnce() {
    fund(50_000L, 10_000L);
    walletService.debitPayment(walletId, 20_000L, 10_000L, 105L);

    walletService.creditPaymentCancel(walletId, 20_000L, 10_000L, 105L);
    walletService.creditPaymentCancel(walletId, 20_000L, 10_000L, 105L);

    assertThat(wallet().getBalance()).isEqualTo(50_000L);
    assertThat(wallet().getCashbackBalance()).isEqualTo(10_000L);
    WalletTransaction devMoney = ledgers(PAYMENT_CANCEL_DEVMONEY).get(0);
    assertThat(devMoney.getAmount()).isEqualTo(20_000L);
    WalletTransaction cashback = ledgers(PAYMENT_CANCEL_CASHBACK).get(0);
    assertThat(cashback.getAmount()).isEqualTo(10_000L);
    assertThat(ledgers(PAYMENT_CANCEL_DEVMONEY)).hasSize(1);
    assertThat(ledgers(PAYMENT_CANCEL_CASHBACK)).hasSize(1);
  }

  @Test
  @DisplayName("같은 결제 ID의 결제 차감이 동시에 와도 한 번만 반영되고 예외가 없다")
  void debitPayment_concurrentDuplicate_appliedOnce() throws Exception {
    fund(50_000L, 10_000L);

    List<Throwable> errors =
        runConcurrently(5, i -> walletService.debitPayment(walletId, 20_000L, 10_000L, 200L));

    assertThat(errors).isEmpty();
    assertThat(wallet().getBalance()).isEqualTo(30_000L);
    assertThat(wallet().getCashbackBalance()).isZero();
    assertThat(ledgers(PAYMENT_DEVMONEY)).hasSize(1);
    assertThat(ledgers(PAYMENT_CASHBACK)).hasSize(1);
  }

  @Test
  @DisplayName("결제 차감에서 충전금이 부족하면 WAL-004로 거절되고 캐시백도 차감되지 않는다")
  void debitPayment_insufficientDevMoney() {
    fund(1_000L, 10_000L);

    assertFails(
        () -> walletService.debitPayment(walletId, 2_000L, 1_000L, 106L),
        WalletErrorCode.INSUFFICIENT_BALANCE);

    assertThat(wallet().getBalance()).isEqualTo(1_000L);
    assertThat(wallet().getCashbackBalance()).isEqualTo(10_000L);
    assertThat(ledgers(PAYMENT_DEVMONEY)).isEmpty();
    assertThat(ledgers(PAYMENT_CASHBACK)).isEmpty();
  }

  @Test
  @DisplayName("결제 차감과 환급은 탈퇴한 지갑이면 WAL-002, 없는 지갑이면 WAL-001로 거절된다")
  void paymentMethods_withdrawnAndMissingWallet() {
    fund(50_000L, 10_000L);
    walletService.debitPayment(walletId, 20_000L, 10_000L, 107L);
    Wallet wallet = wallet();
    wallet.withdraw(NOW);
    walletRepository.save(wallet);

    assertFails(
        () -> walletService.debitPayment(walletId, 1_000L, 0L, 108L),
        WalletErrorCode.WALLET_WITHDRAWN);
    assertFails(
        () -> walletService.creditPaymentCancel(walletId, 20_000L, 10_000L, 107L),
        WalletErrorCode.WALLET_WITHDRAWN);
    assertFails(
        () -> walletService.debitPayment(walletId + 9_999L, 1_000L, 0L, 109L),
        WalletErrorCode.WALLET_NOT_FOUND);
  }

  @Test
  @DisplayName("결제 차감 기록이 없는 결제 ID는 환급할 수 없다")
  void creditPaymentCancel_withoutDebit_rejected() {
    fund(50_000L, 10_000L);

    assertThatThrownBy(() -> walletService.creditPaymentCancel(walletId, 20_000L, 0L, 110L))
        .isInstanceOf(IllegalStateException.class);

    assertThat(wallet().getBalance()).isEqualTo(50_000L);
    assertThat(ledgers(PAYMENT_CANCEL_DEVMONEY)).isEmpty();
  }

  @Test
  @DisplayName("결제 때 낸 금액보다 많이 환급할 수 없다")
  void creditPaymentCancel_overRefund_rejected() {
    fund(50_000L, 10_000L);
    walletService.debitPayment(walletId, 20_000L, 10_000L, 111L);

    assertThatThrownBy(() -> walletService.creditPaymentCancel(walletId, 20_001L, 0L, 111L))
        .isInstanceOf(IllegalStateException.class);
    // 캐시백 칸을 안 낸 만큼 넘게 환급하는 경우도 막음
    assertThatThrownBy(() -> walletService.creditPaymentCancel(walletId, 0L, 10_001L, 111L))
        .isInstanceOf(IllegalStateException.class);

    assertThat(wallet().getBalance()).isEqualTo(30_000L);
    assertThat(wallet().getCashbackBalance()).isZero();
    assertThat(ledgers(PAYMENT_CANCEL_DEVMONEY)).isEmpty();
  }

  @Test
  @DisplayName("결제를 차감한 지갑이 아닌 다른 지갑으로는 환급할 수 없다")
  void creditPaymentCancel_otherWallet_rejected() {
    fund(50_000L, 10_000L);
    walletService.debitPayment(walletId, 20_000L, 0L, 112L);
    Long otherWalletId = walletRepository.save(Wallet.create(2L, NOW)).getId();

    assertThatThrownBy(() -> walletService.creditPaymentCancel(otherWalletId, 20_000L, 0L, 112L))
        .isInstanceOf(IllegalStateException.class);

    assertThat(walletRepository.findById(otherWalletId).orElseThrow().getBalance()).isZero();
  }

  @Test
  @DisplayName("잠그기 전에 지갑을 먼저 읽었어도 최신 잔액으로 차감한다(다른 요청의 입금이 사라지지 않음)")
  void debit_afterEarlierRead_usesLatestBalance() {
    walletService.credit(walletId, 1_000_000L, CHARGE, null);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              walletRepository.findById(walletId); // 먼저 일반 조회(낡은 객체가 남음)
              // 그 사이 다른 트랜잭션이 90만원을 충전하고 커밋
              inNewTransaction(() -> walletService.credit(walletId, 900_000L, CHARGE, null));
              walletService.debit(walletId, 1L, PAYMENT_DEVMONEY, 1L, false);
            });

    assertThat(wallet().getBalance()).isEqualTo(1_899_999L);
  }

  @Test
  @DisplayName("잠그기 전에 지갑을 먼저 읽었어도 다른 요청의 입금이 사라지지 않는다")
  void credit_afterEarlierRead_doesNotLoseOtherDeposit() {
    walletService.credit(walletId, 1_000_000L, CHARGE, null);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              walletRepository.findById(walletId);
              inNewTransaction(() -> walletService.credit(walletId, 900_000L, CHARGE, null));
              walletService.credit(walletId, 1L, CHARGE, null);
            });

    assertThat(wallet().getBalance()).isEqualTo(1_900_001L);
  }

  @Test
  @DisplayName("다른 요청이 캐시백을 입금한 뒤에도, 먼저 읽은 요청의 데브머니 입금이 캐시백 잔액을 덮어쓰지 않는다")
  void credit_afterEarlierRead_doesNotOverwriteOtherColumn() {
    walletService.credit(walletId, 1_000_000L, CHARGE, null);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              walletRepository.findById(walletId);
              // 그 사이 다른 트랜잭션이 캐시백 300원을 지급하고 커밋
              inNewTransaction(() -> walletService.credit(walletId, 300L, CASHBACK_EARN, 1L));
              walletService.credit(walletId, 100_000L, CHARGE, null);
            });

    assertThat(wallet().getBalance()).isEqualTo(1_100_000L);
    assertThat(wallet().getCashbackBalance()).isEqualTo(300L);
  }

  // 별도 트랜잭션으로 실행하고 커밋함. 다른 요청이 끼어든 상황을 만들 때 사용
  private void inNewTransaction(Runnable action) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.executeWithoutResult(status -> action.run());
  }

  // 결제 테스트용 사전 잔액 준비(충전금 입금과 캐시백 지급)
  private void fund(long devMoney, long cashback) {
    walletService.credit(walletId, devMoney, CHARGE, null);
    walletService.credit(walletId, cashback, CASHBACK_EARN, 1L);
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
