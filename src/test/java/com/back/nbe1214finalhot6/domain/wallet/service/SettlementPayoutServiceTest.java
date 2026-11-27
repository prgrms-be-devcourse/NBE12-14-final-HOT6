package com.back.nbe1214finalhot6.domain.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.AccountReader;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// docker compose 의 PostgreSQL 이 켜져 있어야 한다. 테스트 전용 DB(devpay_test)를 사용한다.
@IntegrationTest
@Transactional
class SettlementPayoutServiceTest {

  private static final Long OWNER = 8_501L;
  private static final Long SETTLEMENT_ID = 7_001L;

  @Autowired private SettlementPayoutService payoutService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private AccountTransactionRepository transactionRepository;
  @Autowired private AccountReader accountReader;

  @Test
  @DisplayName("월 정산금을 점주 계좌에 입금하고 월 정산 건 번호로 원장을 남긴다")
  void 입금과_원장() {
    Account account = accountRepository.save(Account.createEmpty(OWNER, LocalDateTime.now()));

    payoutService.payout(OWNER, 15_000L, SETTLEMENT_ID);

    assertThat(accountRepository.findByUserId(OWNER).orElseThrow().getBalance()).isEqualTo(15_000L);
    AccountTransaction tx =
        transactionRepository
            .findByTypeAndRefKey(AccountTransactionType.SETTLEMENT_PAYOUT, SETTLEMENT_ID)
            .orElseThrow();
    assertThat(tx.getAccountId()).isEqualTo(account.getId());
    assertThat(tx.getAmount()).isEqualTo(15_000L);
    assertThat(tx.getBalanceAfter()).isEqualTo(15_000L);
  }

  @Test
  @DisplayName("같은 월 정산 건으로 다시 입금해도 계좌에는 한 번만 들어간다")
  void 같은_건_한번만() {
    accountRepository.save(Account.createEmpty(OWNER, LocalDateTime.now()));

    payoutService.payout(OWNER, 15_000L, SETTLEMENT_ID);
    payoutService.payout(OWNER, 15_000L, SETTLEMENT_ID);

    assertThat(accountRepository.findByUserId(OWNER).orElseThrow().getBalance()).isEqualTo(15_000L);
  }

  @Test
  @DisplayName("같은 월 정산 건인데 금액이 다르면 정산 쪽 오류로 보고 거절한다")
  void 같은_건_금액_다르면_거절() {
    accountRepository.save(Account.createEmpty(OWNER, LocalDateTime.now()));
    payoutService.payout(OWNER, 15_000L, SETTLEMENT_ID);

    assertThatThrownBy(() -> payoutService.payout(OWNER, 20_000L, SETTLEMENT_ID))
        .isInstanceOf(IllegalStateException.class);
    assertThat(accountRepository.findByUserId(OWNER).orElseThrow().getBalance()).isEqualTo(15_000L);
  }

  @Test
  @DisplayName("점주 계좌가 없으면 WAL-008, 금액이 0 이하이면 WAL-003 이다")
  void 계좌없음_금액오류() {
    assertThatThrownBy(() -> payoutService.payout(OWNER, 15_000L, SETTLEMENT_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_NOT_FOUND));
    assertThatThrownBy(() -> payoutService.payout(OWNER, 0L, SETTLEMENT_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(WalletErrorCode.INVALID_AMOUNT));
  }

  @Test
  @DisplayName("정산 입금 뒤에도 계좌 잔액과 원장 합이 같아 계좌 대사가 어긋나지 않는다")
  void 계좌_대사_일치() {
    Account account = accountRepository.save(Account.createEmpty(OWNER, LocalDateTime.now()));

    payoutService.payout(OWNER, 15_000L, SETTLEMENT_ID);
    payoutService.payout(OWNER, 5_000L, SETTLEMENT_ID + 1);
    transactionRepository.flush();

    assertThat(accountReader.findBalanceMismatchIds()).doesNotContain(account.getId());
  }
}
