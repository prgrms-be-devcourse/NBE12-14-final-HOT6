package com.back.nbe1214finalhot6.domain.wallet.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantAccount;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransactionType;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** 가맹점 지갑 테이블이 만들어지고 DB 유니크 제약이 동작하는지 확인함. */
@IntegrationTest
class MerchantWalletRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);

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
  @DisplayName("같은 가맹점의 지갑을 두 번 저장하면 유니크 제약으로 실패한다")
  void 지갑_가맹점ID_중복() {
    walletRepository.saveAndFlush(MerchantWallet.create(1L, NOW));

    assertThatThrownBy(() -> walletRepository.saveAndFlush(MerchantWallet.create(1L, NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("같은 가맹점의 계좌를 두 번 저장하면 유니크 제약으로 실패한다")
  void 계좌_가맹점ID_중복() {
    accountRepository.saveAndFlush(MerchantAccount.create(1L, NOW));

    assertThatThrownBy(() -> accountRepository.saveAndFlush(MerchantAccount.create(1L, NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("같은 타입과 참조 ID의 원장을 두 번 저장하면 유니크 제약으로 실패한다")
  void 원장_타입과참조ID_중복() {
    transactionRepository.saveAndFlush(transaction(MerchantWalletTransactionType.SETTLEMENT_PAYOUT, 1L));

    assertThatThrownBy(
            () ->
                transactionRepository.saveAndFlush(
                    transaction(MerchantWalletTransactionType.SETTLEMENT_PAYOUT, 1L)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("타입이 다르면 같은 참조 ID로 입금과 출금 원장을 함께 저장할 수 있다")
  void 원장_타입이다르면_같은참조ID_허용() {
    transactionRepository.saveAndFlush(transaction(MerchantWalletTransactionType.SETTLEMENT_PAYOUT, 1L));
    transactionRepository.saveAndFlush(transaction(MerchantWalletTransactionType.PAYOUT_TO_ACCOUNT, 1L));

    assertThat(transactionRepository.count()).isEqualTo(2);
  }

  private MerchantWalletTransaction transaction(MerchantWalletTransactionType type, Long refKey) {
    return MerchantWalletTransaction.builder()
        .walletId(1L)
        .type(type)
        .amount(type == MerchantWalletTransactionType.SETTLEMENT_PAYOUT ? 1_000L : -1_000L)
        .balanceAfter(1_000L)
        .refKey(refKey)
        .createdAt(NOW)
        .build();
  }
}
