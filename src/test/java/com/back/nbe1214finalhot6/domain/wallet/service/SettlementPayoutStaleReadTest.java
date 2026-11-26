package com.back.nbe1214finalhot6.domain.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 두 트랜잭션이 각자 커밋해야 하므로 @Transactional 을 붙이지 않고, 끝나면 직접 지운다.
@IntegrationTest
class SettlementPayoutStaleReadTest {

  private static final Long OWNER = 8_601L;

  @Autowired private SettlementPayoutService payoutService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update(
        "delete from account_transaction where account_id in (select id from account where user_id = ?)",
        OWNER);
    jdbcTemplate.update("delete from account where user_id = ?", OWNER);
  }

  @Test
  @DisplayName("지급 전에 같은 트랜잭션에서 계좌를 먼저 읽었어도, 그 사이 다른 지급이 커밋한 잔액을 덮어쓰지 않는다")
  void 먼저_읽은_계좌도_최신_잔액에_입금() throws Exception {
    accountRepository.save(Account.createEmpty(OWNER, LocalDateTime.now()));
    ExecutorService other = Executors.newSingleThreadExecutor();
    try {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                // 1. 이 트랜잭션이 계좌를 먼저 읽는다(잔액 0원인 객체가 영속성 컨텍스트에 남는다)
                assertThat(accountRepository.findByUserId(OWNER).orElseThrow().getBalance()).isZero();

                // 2. 그 사이 다른 트랜잭션이 5,000원을 지급하고 커밋한다
                try {
                  other
                      .submit(
                          () ->
                              new TransactionTemplate(transactionManager)
                                  .executeWithoutResult(s -> payoutService.payout(OWNER, 5_000L, 1L)))
                      .get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }

                // 3. 이 트랜잭션이 10,000원을 지급한다. 낡은 0원에 더하면 10,000원이 되어 5,000원이 사라진다
                payoutService.payout(OWNER, 10_000L, 2L);
              });
    } finally {
      other.shutdownNow();
    }

    assertThat(accountRepository.findByUserId(OWNER).orElseThrow().getBalance()).isEqualTo(15_000L);
  }
}
