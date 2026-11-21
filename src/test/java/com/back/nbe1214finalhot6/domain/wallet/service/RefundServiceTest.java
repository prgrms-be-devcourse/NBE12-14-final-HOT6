package com.back.nbe1214finalhot6.domain.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.entity.Refund;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.ChargeRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.RefundRepository;
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

/** 지갑 → 모의계좌 환불. 계좌 초기 잔액은 200만원이고, 각 테스트는 지갑에 데브머니를 미리 넣어 시작함. */
@IntegrationTest
class RefundServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 22, 10, 0);
  private static final Long USER_ID = 9_701L;
  private static final long FUNDED = 300_000L; // 시작할 때 지갑의 데브머니

  @Autowired RefundService refundService;
  @Autowired ChargeService chargeService;
  @Autowired WalletRepository walletRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired AccountTransactionRepository accountTransactionRepository;
  @Autowired WalletTransactionRepository walletTransactionRepository;
  @Autowired RefundRepository refundRepository;
  @Autowired ChargeRepository chargeRepository;
  @Autowired PlatformTransactionManager transactionManager;

  // 실제 동작은 그대로 두고, 지갑 차감 직후 실패하는 상황만 만들기 위한 spy
  @MockitoSpyBean WalletService walletService;

  private Long walletId;

  @BeforeEach
  void setUp() {
    Wallet wallet = Wallet.create(USER_ID, NOW);
    wallet.credit(FUNDED, false, NOW);
    walletId = walletRepository.save(wallet).getId();
    accountRepository.save(Account.create(USER_ID, NOW));
  }

  // 테스트가 DB에 남긴 행 정리(원장·충전·환불 → 지갑 → 계좌 순)
  @AfterEach
  void cleanUp() {
    accountTransactionRepository.deleteAll();
    walletTransactionRepository.deleteAll();
    refundRepository.deleteAll();
    chargeRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
  }

  @Test
  @DisplayName("환불하면 지갑이 줄고 계좌가 늘며, 지갑 원장(-)과 계좌 원장(+)에 한 줄씩 남는다")
  void refund_movesMoneyAndWritesBothLedgers() {
    RefundResult result = refundService.refund(walletId, 100_000L, "key-1");

    assertThat(wallet().getBalance()).isEqualTo(FUNDED - 100_000L);
    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE + 100_000L);

    Refund refund = refunds().get(0);
    assertThat(result.refundId()).isEqualTo(refund.getId());
    assertThat(refund.getAmount()).isEqualTo(100_000L);

    WalletTransaction walletLedger = walletLedgers().get(0);
    assertThat(walletLedger.getType()).isEqualTo(WalletTransactionType.REFUND);
    assertThat(walletLedger.getAmount()).isEqualTo(-100_000L);
    assertThat(walletLedger.getBalanceAfter()).isEqualTo(FUNDED - 100_000L);
    assertThat(walletLedger.getRefKey()).isEqualTo(refund.getId());

    AccountTransaction accountLedger = accountLedgers().get(0);
    assertThat(accountLedger.getType()).isEqualTo(AccountTransactionType.REFUND);
    assertThat(accountLedger.getAmount()).isEqualTo(100_000L);
    assertThat(accountLedger.getBalanceAfter()).isEqualTo(Account.INITIAL_BALANCE + 100_000L);
    assertThat(accountLedger.getRefKey()).isEqualTo(refund.getId());
  }

  @Test
  @DisplayName("부분 환불을 여러 번 해도 잔액과 원장이 일치한다")
  void partialRefunds_balancesMatchLedgers() {
    refundService.refund(walletId, 100_000L, "key-1");
    refundService.refund(walletId, 50_000L, "key-2");
    refundService.refund(walletId, 150_000L, "key-3");

    assertThat(wallet().getBalance()).isZero();
    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE + FUNDED);
    assertThat(refunds()).hasSize(3);
    // 지갑 원장 합계(-)와 계좌 원장 합계(+)는 크기가 같고, 환불 ID로 한 쌍씩 짝지어짐
    assertThat(walletLedgers().stream().mapToLong(WalletTransaction::getAmount).sum())
        .isEqualTo(-FUNDED);
    assertThat(accountLedgers().stream().mapToLong(AccountTransaction::getAmount).sum())
        .isEqualTo(FUNDED);
    assertThat(walletLedgers().stream().map(WalletTransaction::getRefKey))
        .containsExactlyInAnyOrderElementsOf(
            accountLedgers().stream().map(AccountTransaction::getRefKey).toList());
  }

  @Test
  @DisplayName("데브머니보다 많이 환불하면 WAL-004로 거절되고 아무것도 바뀌지 않는다")
  void exceedsDevMoney_rejected() {
    assertFails(
        () -> refundService.refund(walletId, FUNDED + 1, "key-1"),
        WalletErrorCode.INSUFFICIENT_BALANCE);

    assertNothingChanged();
  }

  @Test
  @DisplayName("캐시백 잔액은 환불할 수 없다(데브머니가 모자라면 캐시백이 많아도 WAL-004)")
  void cashbackIsNotRefundable() {
    Wallet wallet = wallet();
    wallet.credit(1_000_000L, true, NOW); // 캐시백 100만원
    walletRepository.save(wallet);

    assertFails(
        () -> refundService.refund(walletId, FUNDED + 1, "key-1"),
        WalletErrorCode.INSUFFICIENT_BALANCE);

    assertThat(wallet().getCashbackBalance()).isEqualTo(1_000_000L);
    assertNothingChanged();
  }

  @Test
  @DisplayName("음수 캐시백 상태에서도 환불되고 캐시백 잔액은 그대로다")
  void negativeCashback_stillRefundable() {
    Wallet wallet = wallet();
    wallet.credit(100L, true, NOW);
    wallet.debit(300L, true, true, NOW); // 회수로 -200원
    walletRepository.save(wallet);

    refundService.refund(walletId, FUNDED, "key-1");

    assertThat(wallet().getBalance()).isZero();
    assertThat(wallet().getCashbackBalance()).isEqualTo(-200L);
  }

  @Test
  @DisplayName("같은 멱등키·같은 금액으로 다시 요청하면 한 번만 반영되고 처음 결과를 돌려준다")
  void sameKeySameAmount_appliedOnce() {
    RefundResult first = refundService.refund(walletId, 100_000L, "key-1");
    RefundResult second = refundService.refund(walletId, 100_000L, "key-1");

    assertThat(second.refundId()).isEqualTo(first.refundId());
    assertThat(wallet().getBalance()).isEqualTo(FUNDED - 100_000L);
    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE + 100_000L);
    assertThat(refunds()).hasSize(1);
    assertThat(walletLedgers()).hasSize(1);
    assertThat(accountLedgers()).hasSize(1);
  }

  @Test
  @DisplayName("같은 멱등키로 금액이 다르면 COM-003으로 거절되고 처음 환불은 그대로다")
  void sameKeyDifferentAmount_rejected() {
    refundService.refund(walletId, 100_000L, "key-1");

    assertFails(
        () -> refundService.refund(walletId, 200_000L, "key-1"),
        CommonErrorCode.IDEMPOTENCY_KEY_MISMATCH);

    assertThat(wallet().getBalance()).isEqualTo(FUNDED - 100_000L);
    assertThat(refunds()).hasSize(1);
  }

  @Test
  @DisplayName("같은 멱등키가 동시에 와도 한 번만 반영되고 예외가 없다")
  void sameKey_concurrent_appliedOnce() throws Exception {
    List<Throwable> errors =
        runConcurrently(5, i -> refundService.refund(walletId, 100_000L, "same-key"));

    assertThat(errors).isEmpty();
    assertThat(wallet().getBalance()).isEqualTo(FUNDED - 100_000L);
    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE + 100_000L);
    assertThat(refunds()).hasSize(1);
    assertThat(walletLedgers()).hasSize(1);
    assertThat(accountLedgers()).hasSize(1);
  }

  @Test
  @DisplayName("서로 다른 멱등키로 동시에 환불해도 데브머니를 넘지 않고 잔액이 맞는다")
  void differentKeys_concurrent_neverExceedDevMoney() throws Exception {
    // 데브머니 50만원에서 10만원씩 8건을 환불하면 5건만 성공해야 함
    Wallet wallet = wallet();
    wallet.credit(200_000L, false, NOW);
    walletRepository.save(wallet);

    List<Throwable> errors =
        runConcurrently(8, i -> refundService.refund(walletId, 100_000L, "key-" + i));

    assertThat(errors)
        .hasSize(3)
        .allSatisfy(
            e ->
                assertThat(e)
                    .isInstanceOfSatisfying(
                        BusinessException.class,
                        b ->
                            assertThat(b.getErrorCode())
                                .isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE)));
    assertThat(wallet().getBalance()).isZero();
    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE + 500_000L);
    assertThat(refunds()).hasSize(5);
    assertThat(walletLedgers()).hasSize(5);
    assertThat(accountLedgers()).hasSize(5);
  }

  @Test
  @DisplayName("지갑 차감 직후 실패하면 차감과 환불 기록까지 모두 롤백된다")
  void rollback_whenFailsRightAfterWalletDebit() {
    // 실제로 지갑을 차감하고 원장을 남긴 직후에 예외를 던져, 이미 반영된 차감까지 롤백되는지 확인
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("지갑 차감 직후 실패");
            })
        .when(walletService)
        .debit(any(), anyLong(), any(), any(), anyBoolean());

    assertThatThrownBy(() -> refundService.refund(walletId, 100_000L, "key-1"))
        .isInstanceOf(IllegalStateException.class);

    assertNothingChanged();
  }

  @Test
  @DisplayName("0원·없는 지갑·계좌 없음·탈퇴한 지갑·멱등키 없음은 거절한다")
  void invalidRequests() {
    assertFails(() -> refundService.refund(walletId, 0L, "key-1"), WalletErrorCode.INVALID_AMOUNT);
    assertFails(
        () -> refundService.refund(walletId + 9_999L, 1_000L, "key-1"),
        WalletErrorCode.WALLET_NOT_FOUND);
    assertThatThrownBy(() -> refundService.refund(walletId, 1_000L, " "))
        .isInstanceOf(IllegalArgumentException.class);

    // 계좌가 없는 회원의 지갑
    Wallet noAccount = Wallet.create(9_702L, NOW);
    noAccount.credit(50_000L, false, NOW);
    Long noAccountWalletId = walletRepository.save(noAccount).getId();
    assertFails(
        () -> refundService.refund(noAccountWalletId, 1_000L, "key-1"),
        WalletErrorCode.ACCOUNT_NOT_FOUND);

    // 탈퇴한 지갑
    Wallet wallet = wallet();
    wallet.withdraw(NOW);
    walletRepository.save(wallet);
    assertFails(
        () -> refundService.refund(walletId, 1_000L, "key-2"), WalletErrorCode.WALLET_WITHDRAWN);
  }

  @Test
  @DisplayName("환불해도 월 충전 한도는 복원되지 않는다")
  void refund_doesNotRestoreMonthlyChargeLimit() {
    chargeService.charge(walletId, 600_000L, "charge-1"); // 이번 달 충전 60만원
    long devMoney = wallet().getBalance();
    refundService.refund(walletId, devMoney, "refund-1"); // 전액 환불

    // 환불했어도 이번 달 충전 누적은 60만원 그대로라 50만원 충전은 월 한도(100만원)를 넘음
    assertFails(
        () -> chargeService.charge(walletId, 500_000L, "charge-2"),
        WalletErrorCode.MONTHLY_CHARGE_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("충전한 만큼 환불하면 계좌와 지갑이 처음으로 돌아온다")
  void chargeThenRefund_roundTrip() {
    long accountBefore = account().getBalance();
    long walletBefore = wallet().getBalance();

    chargeService.charge(walletId, 100_000L, "charge-1");
    refundService.refund(walletId, 100_000L, "refund-1");

    assertThat(account().getBalance()).isEqualTo(accountBefore);
    assertThat(wallet().getBalance()).isEqualTo(walletBefore);
  }

  @Test
  @DisplayName("잠그기 전에 계좌를 먼저 읽었어도 최신 잔액으로 입금한다(다른 요청의 환불이 사라지지 않음)")
  void accountReadEarlier_usesLatestBalance() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              accountRepository.findByUserId(USER_ID); // 먼저 일반 조회(낡은 객체가 남음)
              // 그 사이 다른 트랜잭션이 10만원을 환불하고 커밋
              inNewTransaction(() -> refundService.refund(walletId, 100_000L, "key-1"));
              refundService.refund(walletId, 100_000L, "key-2");
            });

    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE + 200_000L);
    assertThat(wallet().getBalance()).isEqualTo(FUNDED - 200_000L);
  }

  @Test
  @DisplayName("잠그기 전에 지갑을 먼저 읽었어도 다른 요청의 환불이 사라지지 않는다")
  void walletReadEarlier_doesNotLoseOtherRefund() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              walletRepository.findById(walletId); // 먼저 일반 조회(낡은 객체가 남음)
              inNewTransaction(() -> refundService.refund(walletId, 100_000L, "key-1"));
              refundService.refund(walletId, 100_000L, "key-2");
            });

    assertThat(wallet().getBalance()).isEqualTo(FUNDED - 200_000L);
    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE + 200_000L);
  }

  // 환불이 실패했을 때 지갑·계좌·환불·원장이 모두 처음 그대로인지 확인
  private void assertNothingChanged() {
    assertThat(wallet().getBalance()).isEqualTo(FUNDED);
    assertThat(account().getBalance()).isEqualTo(Account.INITIAL_BALANCE);
    assertThat(refunds()).isEmpty();
    assertThat(walletLedgers()).isEmpty();
    assertThat(accountLedgers()).isEmpty();
  }

  private Wallet wallet() {
    return walletRepository.findById(walletId).orElseThrow();
  }

  private Account account() {
    return accountRepository.findByUserId(USER_ID).orElseThrow();
  }

  // 이 테스트의 지갑·계좌에 쌓인 행만 조회(다른 데이터에 영향받지 않도록)
  private List<Refund> refunds() {
    return refundRepository.findAll().stream()
        .filter(r -> r.getWalletId().equals(walletId))
        .toList();
  }

  private List<AccountTransaction> accountLedgers() {
    Long accountId = account().getId();
    return accountTransactionRepository.findAll().stream()
        .filter(t -> t.getAccountId().equals(accountId))
        .filter(t -> t.getType() == AccountTransactionType.REFUND)
        .toList();
  }

  private List<WalletTransaction> walletLedgers() {
    return walletTransactionRepository.findAll().stream()
        .filter(t -> t.getWalletId().equals(walletId))
        .filter(t -> t.getType() == WalletTransactionType.REFUND)
        .toList();
  }

  // 별도 트랜잭션으로 실행하고 커밋함. 다른 요청이 끼어든 상황을 만들 때 사용
  private void inNewTransaction(Runnable action) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.executeWithoutResult(status -> action.run());
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
