package com.back.nbe1214finalhot6.domain.wallet.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** 엔티티가 테이블로 만들어지고 DB 유니크 제약이 동작하는지 확인함. */
@IntegrationTest
class WalletRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 10, 0);

  @Autowired WalletRepository walletRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired WalletTransactionRepository walletTransactionRepository;

  // 테스트가 DB에 남긴 행 정리(원장 → 지갑 → 계좌 순)
  @AfterEach
  void cleanUp() {
    walletTransactionRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
  }

  @Test
  @DisplayName("같은 회원의 지갑을 두 번 저장하면 유니크 제약으로 실패한다")
  void wallet_duplicateUserId() {
    walletRepository.saveAndFlush(Wallet.create(1L, NOW));

    assertThatThrownBy(() -> walletRepository.saveAndFlush(Wallet.create(1L, NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("같은 회원의 계좌를 두 번 저장하면 유니크 제약으로 실패한다")
  void account_duplicateUserId() {
    accountRepository.saveAndFlush(Account.create(1L, NOW));

    assertThatThrownBy(() -> accountRepository.saveAndFlush(Account.create(1L, NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("계좌는 초기 잔액 100만원으로 저장된다")
  void account_initialBalance() {
    Account saved = accountRepository.saveAndFlush(Account.create(1L, NOW));

    // 저장한 객체가 아니라 DB에서 다시 읽은 값으로 확인
    Account found = accountRepository.findById(saved.getId()).orElseThrow();

    assertThat(found.getBalance()).isEqualTo(1_000_000L);
  }

  @Test
  @DisplayName("같은 (type, refKey)의 원장은 두 번 저장할 수 없고, type이 다르면 저장할 수 있다")
  void walletTransaction_uniqueTypeRefKey() {
    walletTransactionRepository.saveAndFlush(ledger(WalletTransactionType.CASHBACK_EARN, 100L));

    // 같은 type, 같은 refKey: 중복 반영이므로 실패
    assertThatThrownBy(
            () ->
                walletTransactionRepository.saveAndFlush(
                    ledger(WalletTransactionType.CASHBACK_EARN, 100L)))
        .isInstanceOf(DataIntegrityViolationException.class);

    // 같은 refKey, 다른 type: 같은 적립 건의 지급과 회수는 허용
    walletTransactionRepository.saveAndFlush(ledger(WalletTransactionType.CASHBACK_CLAWBACK, 100L));

    assertThat(walletTransactionRepository.count()).isEqualTo(2);
  }

  // 테스트용 원장 행. 유니크 검증에 필요한 type, refKey만 바꿔가며 사용
  private WalletTransaction ledger(WalletTransactionType type, Long refKey) {
    return WalletTransaction.builder()
        .walletId(1L)
        .type(type)
        .amount(-1_000L) // 부호형: 차감은 음수
        .balanceAfter(1_000L)
        .refKey(refKey)
        .createdAt(NOW)
        .build();
  }
}
