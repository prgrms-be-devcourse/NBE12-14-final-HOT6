package com.back.nbe1214finalhot6.domain.wallet.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** 계좌 원장의 (type, ref_key) 유니크 제약이 동작하는지 확인함. */
@IntegrationTest
class AccountTransactionRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

  @Autowired AccountTransactionRepository accountTransactionRepository;

  @AfterEach
  void cleanUp() {
    accountTransactionRepository.deleteAll();
  }

  @Test
  @DisplayName("같은 (type, ref_key)의 계좌 원장은 두 번 저장할 수 없다")
  void duplicateTypeRefKey_rejected() {
    accountTransactionRepository.saveAndFlush(ledger(AccountTransactionType.CHARGE, 100L));

    assertThatThrownBy(
            () ->
                accountTransactionRepository.saveAndFlush(
                    ledger(AccountTransactionType.CHARGE, 100L)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("ref_key가 null인 거래(가입 보너스)는 여러 줄 저장할 수 있다")
  void nullRefKey_allowedMultipleTimes() {
    long before = accountTransactionRepository.count();

    accountTransactionRepository.saveAndFlush(ledger(AccountTransactionType.SIGNUP_BONUS, null));
    accountTransactionRepository.saveAndFlush(ledger(AccountTransactionType.SIGNUP_BONUS, null));

    assertThat(accountTransactionRepository.count()).isEqualTo(before + 2);
  }

  private AccountTransaction ledger(AccountTransactionType type, Long refKey) {
    return AccountTransaction.builder()
        .accountId(1L)
        .type(type)
        .amount(-1_000L)
        .balanceAfter(1_000L)
        .refKey(refKey)
        .createdAt(NOW)
        .build();
  }
}
