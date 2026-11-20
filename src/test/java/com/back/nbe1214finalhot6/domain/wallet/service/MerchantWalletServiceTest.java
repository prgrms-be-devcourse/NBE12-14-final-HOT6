package com.back.nbe1214finalhot6.domain.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.wallet.dto.MerchantWalletTransactionResponse;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantAccount;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.MerchantWalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletTransactionRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class MerchantWalletServiceTest {

  private static final Long MERCHANT_ID = 100L;

  @Autowired MerchantWalletService service;
  @Autowired MerchantWalletRepository walletRepository;
  @Autowired MerchantAccountRepository accountRepository;
  @Autowired MerchantWalletTransactionRepository transactionRepository;

  @AfterEach
  void cleanUp() {
    transactionRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
  }

  @Test
  @DisplayName("지갑과 계좌를 만들면 둘 다 잔액 0원으로 저장된다")
  void 생성시_잔액0원() {
    service.createFor(MERCHANT_ID);

    assertThat(walletRepository.findByMerchantId(MERCHANT_ID).orElseThrow().getBalance()).isZero();
    assertThat(accountRepository.findByMerchantId(MERCHANT_ID).orElseThrow().getBalance())
        .isZero();
  }

  @Test
  @DisplayName("입금하면 지갑 잔액이 늘고 원장에 양수 금액으로 기록된다")
  void 입금_잔액증가와_원장기록() {
    service.createFor(MERCHANT_ID);

    MerchantWalletTransactionResponse response = service.deposit(MERCHANT_ID, 10_000L, 1L);

    assertThat(response.amount()).isEqualTo(10_000L);
    assertThat(response.balanceAfter()).isEqualTo(10_000L);
    assertThat(walletRepository.findByMerchantId(MERCHANT_ID).orElseThrow().getBalance())
        .isEqualTo(10_000L);
    MerchantWalletTransaction saved =
        transactionRepository
            .findByTypeAndRefKey(MerchantWalletTransactionType.SETTLEMENT_PAYOUT, 1L)
            .orElseThrow();
    assertThat(saved.getAmount()).isEqualTo(10_000L);
  }

  @Test
  @DisplayName("같은 정산 건으로 입금을 두 번 호출해도 한 번만 반영된다")
  void 입금_중복호출은_한번만반영() {
    service.createFor(MERCHANT_ID);

    service.deposit(MERCHANT_ID, 10_000L, 1L);
    MerchantWalletTransactionResponse second = service.deposit(MERCHANT_ID, 10_000L, 1L);

    assertThat(second.balanceAfter()).isEqualTo(10_000L);
    assertThat(walletRepository.findByMerchantId(MERCHANT_ID).orElseThrow().getBalance())
        .isEqualTo(10_000L);
    assertThat(transactionRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("같은 정산 건으로 다른 금액을 입금하면 거절되고 잔액은 그대로다")
  void 입금_같은정산건_다른금액() {
    service.createFor(MERCHANT_ID);
    service.deposit(MERCHANT_ID, 10_000L, 1L);

    assertThatThrownBy(() -> service.deposit(MERCHANT_ID, 20_000L, 1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantWalletErrorCode.AMOUNT_MISMATCH));
    assertThat(walletRepository.findByMerchantId(MERCHANT_ID).orElseThrow().getBalance())
        .isEqualTo(10_000L);
  }

  @Test
  @DisplayName("같은 정산 건으로 다른 금액을 출금하면 거절되고 잔액은 그대로다")
  void 출금_같은정산건_다른금액() {
    service.createFor(MERCHANT_ID);
    service.deposit(MERCHANT_ID, 10_000L, 1L);
    service.withdrawToAccount(MERCHANT_ID, 4_000L, 1L);

    assertThatThrownBy(() -> service.withdrawToAccount(MERCHANT_ID, 5_000L, 1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantWalletErrorCode.AMOUNT_MISMATCH));
    assertThat(walletRepository.findByMerchantId(MERCHANT_ID).orElseThrow().getBalance())
        .isEqualTo(6_000L);
    assertThat(accountRepository.findByMerchantId(MERCHANT_ID).orElseThrow().getBalance())
        .isEqualTo(4_000L);
  }

  @Test
  @DisplayName("같은 정산 건으로 동시에 입금해도 한 번만 반영된다")
  void 입금_동시호출은_한번만반영() throws Exception {
    service.createFor(MERCHANT_ID);
    int threads = 5;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);

    List<Future<?>> futures =
        java.util.stream.IntStream.range(0, threads)
            .mapToObj(
                i ->
                    executor.submit(
                        () -> {
                          ready.countDown();
                          start.await();
                          return service.deposit(MERCHANT_ID, 10_000L, 1L);
                        }))
            .collect(java.util.stream.Collectors.toList());
    ready.await();
    start.countDown();
    for (Future<?> future : futures) {
      future.get();
    }
    executor.shutdown();

    assertThat(walletRepository.findByMerchantId(MERCHANT_ID).orElseThrow().getBalance())
        .isEqualTo(10_000L);
    assertThat(transactionRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("0원 이하 금액으로 입금하면 거절된다")
  void 입금_금액오류() {
    service.createFor(MERCHANT_ID);

    assertThatThrownBy(() -> service.deposit(MERCHANT_ID, 0L, 1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantWalletErrorCode.INVALID_AMOUNT));
  }

  @Test
  @DisplayName("지갑이 없는 가맹점에 입금하면 거절된다")
  void 입금_지갑없음() {
    assertThatThrownBy(() -> service.deposit(MERCHANT_ID, 10_000L, 1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(MerchantWalletErrorCode.MERCHANT_WALLET_NOT_FOUND));
  }

  @Test
  @DisplayName("출금하면 지갑은 줄고 계좌는 늘며 원장에 음수 금액으로 기록된다")
  void 출금_지갑차감_계좌증가_원장기록() {
    service.createFor(MERCHANT_ID);
    service.deposit(MERCHANT_ID, 10_000L, 1L);

    MerchantWalletTransactionResponse response = service.withdrawToAccount(MERCHANT_ID, 4_000L, 1L);

    assertThat(response.amount()).isEqualTo(-4_000L);
    assertThat(response.balanceAfter()).isEqualTo(6_000L);
    MerchantWallet wallet = walletRepository.findByMerchantId(MERCHANT_ID).orElseThrow();
    MerchantAccount account = accountRepository.findByMerchantId(MERCHANT_ID).orElseThrow();
    assertThat(wallet.getBalance()).isEqualTo(6_000L);
    assertThat(account.getBalance()).isEqualTo(4_000L);
  }

  @Test
  @DisplayName("같은 정산 건으로 출금을 두 번 호출해도 한 번만 반영된다")
  void 출금_중복호출은_한번만반영() {
    service.createFor(MERCHANT_ID);
    service.deposit(MERCHANT_ID, 10_000L, 1L);

    service.withdrawToAccount(MERCHANT_ID, 4_000L, 1L);
    service.withdrawToAccount(MERCHANT_ID, 4_000L, 1L);

    assertThat(walletRepository.findByMerchantId(MERCHANT_ID).orElseThrow().getBalance())
        .isEqualTo(6_000L);
    assertThat(accountRepository.findByMerchantId(MERCHANT_ID).orElseThrow().getBalance())
        .isEqualTo(4_000L);
  }

  @Test
  @DisplayName("잔액보다 큰 금액을 출금하면 거절되고 잔액은 그대로다")
  void 출금_잔액부족() {
    service.createFor(MERCHANT_ID);
    service.deposit(MERCHANT_ID, 10_000L, 1L);

    assertThatThrownBy(() -> service.withdrawToAccount(MERCHANT_ID, 10_001L, 1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(MerchantWalletErrorCode.INSUFFICIENT_BALANCE));
    assertThat(walletRepository.findByMerchantId(MERCHANT_ID).orElseThrow().getBalance())
        .isEqualTo(10_000L);
    assertThat(accountRepository.findByMerchantId(MERCHANT_ID).orElseThrow().getBalance())
        .isZero();
  }

  @Test
  @DisplayName("계좌가 없으면 출금이 거절되고 지갑 잔액은 그대로다")
  void 출금_계좌없음() {
    service.createFor(MERCHANT_ID);
    service.deposit(MERCHANT_ID, 10_000L, 1L);
    accountRepository.deleteAll();

    assertThatThrownBy(() -> service.withdrawToAccount(MERCHANT_ID, 4_000L, 1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(MerchantWalletErrorCode.MERCHANT_ACCOUNT_NOT_FOUND));
    assertThat(walletRepository.findByMerchantId(MERCHANT_ID).orElseThrow().getBalance())
        .isEqualTo(10_000L);
  }
}
