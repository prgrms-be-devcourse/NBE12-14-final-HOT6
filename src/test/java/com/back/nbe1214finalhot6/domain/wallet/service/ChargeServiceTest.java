package com.back.nbe1214finalhot6.domain.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.entity.Charge;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.ChargeRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 모의계좌 → 지갑 충전. 한도는 기본값(보유 200만원, 월 충전 100만원), 계좌 초기 잔액은 200만원. */
@IntegrationTest
class ChargeServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);
  private static final Long USER_ID = 9_501L;

  @Autowired ChargeService chargeService;
  @Autowired WalletRepository walletRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired AccountTransactionRepository accountTransactionRepository;
  @Autowired WalletTransactionRepository walletTransactionRepository;
  @Autowired ChargeRepository chargeRepository;
  @Autowired PlatformTransactionManager transactionManager;

  // 실제 동작은 그대로 두고, 지갑 입금이 실패하는 상황만 만들기 위한 spy
  @MockitoSpyBean WalletService walletService;

  private Long walletId;

  @BeforeEach
  void setUp() {
    walletId = walletRepository.save(Wallet.create(USER_ID, NOW)).getId();
    accountRepository.save(Account.create(USER_ID, NOW));
  }

  // 테스트가 DB에 남긴 행 정리(원장·충전 → 지갑 → 계좌 순)
  @AfterEach
  void cleanUp() {
    accountTransactionRepository.deleteAll();
    walletTransactionRepository.deleteAll();
    chargeRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
  }

  @Test
  @DisplayName("충전하면 계좌가 줄고 지갑이 늘며, 계좌 원장(-)과 지갑 원장(+)에 한 줄씩 남는다")
  void charge_movesMoneyAndWritesBothLedgers() {
    ChargeResult result = chargeService.charge(walletId, 100_000L, "key-1");

    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE - 100_000L);
    assertThat(wallet().getBalance()).isEqualTo(100_000L);

    Charge charge = charges().get(0);
    assertThat(result.chargeId()).isEqualTo(charge.getId());
    assertThat(charge.getAmount()).isEqualTo(100_000L);

    AccountTransaction accountLedger = accountLedgers().get(0);
    assertThat(accountLedger.getType()).isEqualTo(AccountTransactionType.CHARGE);
    assertThat(accountLedger.getAmount()).isEqualTo(-100_000L);
    assertThat(accountLedger.getBalanceAfter()).isEqualTo(Account.INITIAL_BALANCE - 100_000L);
    assertThat(accountLedger.getRefKey()).isEqualTo(charge.getId());

    WalletTransaction walletLedger = walletLedgers().get(0);
    assertThat(walletLedger.getType()).isEqualTo(WalletTransactionType.CHARGE);
    assertThat(walletLedger.getAmount()).isEqualTo(100_000L);
    assertThat(walletLedger.getBalanceAfter()).isEqualTo(100_000L);
    assertThat(walletLedger.getRefKey()).isEqualTo(charge.getId());
  }

  @Test
  @DisplayName("계좌 잔액이 부족하면 WAL-009로 거절되고 아무것도 바뀌지 않는다")
  void insufficientAccountBalance() {
    Account account = account();
    account.debit(1_950_000L); // 계좌 잔액 5만원
    accountRepository.save(account);

    assertFails(
        () -> chargeService.charge(walletId, 100_000L, "key-1"),
        WalletErrorCode.INSUFFICIENT_ACCOUNT_BALANCE);

    assertNothingChanged(50_000L);
  }

  @Test
  @DisplayName("같은 멱등키·같은 금액으로 다시 요청하면 한 번만 반영되고 처음 결과를 돌려준다")
  void sameKeySameAmount_appliedOnce() {
    ChargeResult first = chargeService.charge(walletId, 100_000L, "key-1");
    ChargeResult second = chargeService.charge(walletId, 100_000L, "key-1");

    assertThat(second.chargeId()).isEqualTo(first.chargeId());
    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE - 100_000L);
    assertThat(wallet().getBalance()).isEqualTo(100_000L);
    assertThat(charges()).hasSize(1);
    assertThat(accountLedgers()).hasSize(1);
    assertThat(walletLedgers()).hasSize(1);
  }

  @Test
  @DisplayName("같은 멱등키로 금액이 다르면 COM-003으로 거절되고 처음 충전은 그대로다")
  void sameKeyDifferentAmount_rejected() {
    chargeService.charge(walletId, 100_000L, "key-1");

    assertFails(
        () -> chargeService.charge(walletId, 200_000L, "key-1"),
        CommonErrorCode.IDEMPOTENCY_KEY_MISMATCH);

    assertThat(wallet().getBalance()).isEqualTo(100_000L);
    assertThat(charges()).hasSize(1);
  }

  @Test
  @DisplayName("같은 멱등키가 동시에 와도 한 번만 반영되고 예외가 없다")
  void sameKey_concurrent_appliedOnce() throws Exception {
    List<Throwable> errors =
        runConcurrently(5, i -> chargeService.charge(walletId, 100_000L, "same-key"));

    assertThat(errors).isEmpty();
    assertThat(wallet().getBalance()).isEqualTo(100_000L);
    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE - 100_000L);
    assertThat(charges()).hasSize(1);
    assertThat(accountLedgers()).hasSize(1);
    assertThat(walletLedgers()).hasSize(1);
  }

  @Test
  @DisplayName("서로 다른 멱등키로 동시에 충전해도 월 충전 한도를 넘지 않고 잔액이 맞는다")
  void differentKeys_concurrent_respectMonthlyLimit() throws Exception {
    // 월 한도 100만원이므로 10만원씩 12건 중 10건만 성공해야 함
    List<Throwable> errors =
        runConcurrently(12, i -> chargeService.charge(walletId, 100_000L, "key-" + i));

    assertThat(errors)
        .hasSize(2)
        .allSatisfy(
            e ->
                assertThat(e)
                    .isInstanceOfSatisfying(
                        BusinessException.class,
                        b ->
                            assertThat(b.getErrorCode())
                                .isEqualTo(WalletErrorCode.MONTHLY_CHARGE_LIMIT_EXCEEDED)));
    assertThat(wallet().getBalance()).isEqualTo(1_000_000L);
    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE - 1_000_000L);
    assertThat(charges()).hasSize(10);
    assertThat(accountLedgers()).hasSize(10);
    assertThat(walletLedgers()).hasSize(10);
  }

  @Test
  @DisplayName("지갑 입금에서 실패하면 계좌 차감과 충전 기록까지 모두 롤백된다")
  void rollback_whenWalletCreditFails() {
    doThrow(new IllegalStateException("지갑 입금 실패"))
        .when(walletService)
        .credit(any(), anyLong(), any(), any());

    assertThatThrownBy(() -> chargeService.charge(walletId, 100_000L, "key-1"))
        .isInstanceOf(IllegalStateException.class);

    assertNothingChanged(Account.INITIAL_BALANCE);
  }

  @Test
  @DisplayName("0원·없는 지갑·계좌 없음·탈퇴한 지갑·멱등키 없음은 거절한다")
  void invalidRequests() {
    assertFails(() -> chargeService.charge(walletId, 0L, "key-1"), WalletErrorCode.INVALID_AMOUNT);
    assertFails(
        () -> chargeService.charge(walletId + 9_999L, 1_000L, "key-1"),
        WalletErrorCode.WALLET_NOT_FOUND);
    assertThatThrownBy(() -> chargeService.charge(walletId, 1_000L, " "))
        .isInstanceOf(IllegalArgumentException.class);

    // 계좌가 없는 회원의 지갑
    Long noAccountWalletId = walletRepository.save(Wallet.create(9_502L, NOW)).getId();
    assertFails(
        () -> chargeService.charge(noAccountWalletId, 1_000L, "key-1"),
        WalletErrorCode.ACCOUNT_NOT_FOUND);

    // 탈퇴한 지갑
    Wallet wallet = wallet();
    wallet.withdraw(NOW);
    walletRepository.save(wallet);
    assertFails(
        () -> chargeService.charge(walletId, 1_000L, "key-2"), WalletErrorCode.WALLET_WITHDRAWN);
  }

  @Test
  @DisplayName("보유 한도를 넘는 충전은 WAL-006으로 거절되고 계좌는 그대로다")
  void holdingLimitExceeded() {
    Wallet wallet = wallet();
    wallet.credit(1_900_000L, false, NOW); // 지갑에 이미 190만원
    walletRepository.save(wallet);

    assertFails(
        () -> chargeService.charge(walletId, 100_001L, "key-1"),
        WalletErrorCode.HOLDING_LIMIT_EXCEEDED);

    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE);
    assertThat(charges()).isEmpty();
    // 딱 한도와 같은 금액은 충전됨
    chargeService.charge(walletId, 100_000L, "key-2");
    assertThat(wallet().getBalance()).isEqualTo(2_000_000L);
  }

  @Test
  @DisplayName("월 충전 한도를 넘는 충전은 WAL-007로 거절되고 앞선 충전은 그대로다")
  void monthlyLimitExceeded() {
    chargeService.charge(walletId, 600_000L, "key-1");

    assertFails(
        () -> chargeService.charge(walletId, 400_001L, "key-2"),
        WalletErrorCode.MONTHLY_CHARGE_LIMIT_EXCEEDED);

    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE - 600_000L);
    assertThat(charges()).hasSize(1);
    // 딱 한도와 같은 금액은 충전됨
    chargeService.charge(walletId, 400_000L, "key-3");
    assertThat(wallet().getBalance()).isEqualTo(1_000_000L);
  }

  @Test
  @DisplayName("잠그기 전에 계좌를 먼저 읽었어도 최신 잔액으로 차감한다(다른 요청의 충전이 사라지지 않음)")
  void accountReadEarlier_usesLatestBalance() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              accountRepository.findByUserId(USER_ID); // 먼저 일반 조회(낡은 객체가 남음)
              // 그 사이 다른 트랜잭션이 10만원을 충전하고 커밋
              inNewTransaction(() -> chargeService.charge(walletId, 100_000L, "key-1"));
              chargeService.charge(walletId, 100_000L, "key-2");
            });

    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE - 200_000L);
    assertThat(wallet().getBalance()).isEqualTo(200_000L);
  }

  @Test
  @DisplayName("잠그기 전에 지갑을 먼저 읽었어도 다른 요청의 충전이 사라지지 않는다")
  void walletReadEarlier_doesNotLoseOtherCharge() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              walletRepository.findById(walletId); // 먼저 일반 조회(낡은 객체가 남음)
              inNewTransaction(() -> chargeService.charge(walletId, 100_000L, "key-1"));
              chargeService.charge(walletId, 100_000L, "key-2");
            });

    assertThat(wallet().getBalance()).isEqualTo(200_000L);
    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE - 200_000L);
  }

  // 충전이 실패했을 때 계좌 잔액·지갑·충전·원장이 모두 처음 그대로인지 확인
  private void assertNothingChanged(long accountBalance) {
    assertThat(account().getBalance()).isEqualTo(accountBalance);
    assertThat(wallet().getBalance()).isZero();
    assertThat(charges()).isEmpty();
    assertThat(accountLedgers()).isEmpty();
    assertThat(walletLedgers()).isEmpty();
  }

  // 별도 트랜잭션으로 실행하고 커밋함. 다른 요청이 끼어든 상황을 만들 때 사용
  private void inNewTransaction(Runnable action) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.executeWithoutResult(status -> action.run());
  }

  private Wallet wallet() {
    return walletRepository.findById(walletId).orElseThrow();
  }

  private Account account() {
    return accountRepository.findByUserId(USER_ID).orElseThrow();
  }

  // 이 테스트의 지갑·계좌에 쌓인 행만 조회(다른 데이터에 영향받지 않도록)
  private List<Charge> charges() {
    return chargeRepository.findAll().stream().filter(c -> c.getWalletId().equals(walletId)).toList();
  }

  private List<AccountTransaction> accountLedgers() {
    Long accountId = account().getId();
    return accountTransactionRepository.findAll().stream()
        .filter(t -> t.getAccountId().equals(accountId))
        .filter(t -> t.getType() == AccountTransactionType.CHARGE)
        .toList();
  }

  private List<WalletTransaction> walletLedgers() {
    return walletTransactionRepository.findAll().stream()
        .filter(t -> t.getWalletId().equals(walletId))
        .toList();
  }

  private void assertFails(ThrowingCallable call, ErrorCode expected) {
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
    assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
    return errors;
  }
}
