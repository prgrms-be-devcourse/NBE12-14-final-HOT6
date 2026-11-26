package com.back.nbe1214finalhot6.domain.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletStatus;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class WalletOpeningServiceTest {

  private static final Long USER_ID = 7_001L;

  @Autowired WalletOpeningService walletOpeningService;
  @Autowired AccountRepository accountRepository;
  @Autowired AccountTransactionRepository accountTransactionRepository;
  @Autowired WalletRepository walletRepository;

  // 테스트가 DB에 남긴 행 정리(원장 → 지갑 → 계좌 순)
  @AfterEach
  void cleanUp() {
    accountTransactionRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
  }

  @Test
  @DisplayName("모의계좌(500만원)와 가입 보너스 원장 1줄, 지갑(잔액 0, ACTIVE)이 만들어진다")
  void open_createsAccountLedgerAndWallet() {
    walletOpeningService.open(USER_ID);

    Account account = accountRepository.findByUserId(USER_ID).orElseThrow();
    assertThat(account.getBalance()).isEqualTo(5_000_000L);

    List<AccountTransaction> ledgers = ledgersOf(account.getId());
    assertThat(ledgers).hasSize(1);
    AccountTransaction ledger = ledgers.get(0);
    assertThat(ledger.getType()).isEqualTo(AccountTransactionType.SIGNUP_BONUS);
    assertThat(ledger.getAmount()).isEqualTo(5_000_000L);
    assertThat(ledger.getBalanceAfter()).isEqualTo(5_000_000L);

    Wallet wallet = walletRepository.findByUserId(USER_ID).orElseThrow();
    assertThat(wallet.getBalance()).isZero();
    assertThat(wallet.getCashbackBalance()).isZero();
    assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
  }

  @Test
  @DisplayName("같은 회원에게 다시 만들면 유니크 제약으로 실패하고 아무것도 늘지 않는다")
  void open_twice_fails() {
    walletOpeningService.open(USER_ID);

    assertThatThrownBy(() -> walletOpeningService.open(USER_ID))
        .isInstanceOf(DataIntegrityViolationException.class);

    Account account = accountRepository.findByUserId(USER_ID).orElseThrow();
    assertThat(ledgersOf(account.getId())).hasSize(1);
    assertThat(walletRepository.findByUserId(USER_ID)).isPresent();
  }

  // 이 테스트의 계좌에 쌓인 원장만 조회(다른 데이터에 영향받지 않도록)
  private List<AccountTransaction> ledgersOf(Long accountId) {
    return accountTransactionRepository.findAll().stream()
        .filter(t -> t.getAccountId().equals(accountId))
        .toList();
  }
}
