package com.back.nbe1214finalhot6.domain.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.wallet.dto.AccountTransactionResponse;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 내 모의계좌 잔액·거래 내역 서비스를 실제 DB 로 확인한다. 테스트가 만든 계좌와 원장만 직접 지운다. */
@IntegrationTest
class AccountQueryServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 9, 0);

  @Autowired AccountQueryService accountQueryService;
  @Autowired AccountRepository accountRepository;
  @Autowired AccountTransactionRepository accountTransactionRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  private final List<Long> accountIds = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    for (Long id : accountIds) {
      jdbcTemplate.update("delete from account_transaction where account_id = ?", id);
      jdbcTemplate.update("delete from account where id = ?", id);
    }
    accountIds.clear();
  }

  private Account account(long userId) {
    Account account = accountRepository.save(Account.create(userId, NOW));
    accountIds.add(account.getId());
    return account;
  }

  private AccountTransaction ledger(
      Account account, AccountTransactionType type, long amount, long balanceAfter, Long refKey, LocalDateTime at) {
    return accountTransactionRepository.save(
        AccountTransaction.builder()
            .accountId(account.getId())
            .type(type)
            .amount(amount)
            .balanceAfter(balanceAfter)
            .refKey(refKey)
            .createdAt(at)
            .build());
  }

  // 목적: 내 계좌 잔액만 나오고 다른 회원의 계좌와 섞이지 않는지 확인한다.
  @Test
  @DisplayName("내 계좌 잔액만 조회되고 다른 회원의 계좌는 섞이지 않는다")
  void 잔액_내_계좌만() {
    Account mine = account(9_801_001L);
    Account other = account(9_801_002L);
    jdbcTemplate.update("update account set balance = 1850000 where id = ?", mine.getId());
    jdbcTemplate.update("update account set balance = 5 where id = ?", other.getId());

    assertThat(accountQueryService.getAccount(9_801_001L).balance()).isEqualTo(1_850_000L);
    assertThat(accountQueryService.getAccount(9_801_002L).balance()).isEqualTo(5L);
  }

  @Test
  @DisplayName("계좌가 없는 회원은 잔액·내역 모두 404(WAL-008)이다")
  void 계좌_없음() {
    assertThatThrownBy(() -> accountQueryService.getAccount(9_801_999L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_NOT_FOUND));
    assertThatThrownBy(() -> accountQueryService.getTransactions(9_801_999L, 0, 10))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_NOT_FOUND));
  }

  // 목적: 내 계좌의 원장만 나오고 다른 계좌의 거래가 섞이지 않는지 확인한다.
  @Test
  @DisplayName("내 계좌의 거래만 조회되고 다른 회원 계좌의 거래는 섞이지 않는다")
  void 내역_내_계좌만() {
    Account mine = account(9_802_001L);
    Account other = account(9_802_002L);
    AccountTransaction m1 = ledger(mine, AccountTransactionType.SIGNUP_BONUS, 2_000_000L, 2_000_000L, null, NOW);
    AccountTransaction m2 = ledger(mine, AccountTransactionType.CHARGE, -100_000L, 1_900_000L, 1L, NOW.plusMinutes(1));
    ledger(other, AccountTransactionType.CHARGE, -777L, 1_999_223L, 2L, NOW);

    PageResponse<AccountTransactionResponse> page = accountQueryService.getTransactions(9_802_001L, 0, 10);

    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content())
        .extracting(AccountTransactionResponse::transactionId)
        .containsExactlyInAnyOrder(m1.getId(), m2.getId());
  }

  // 목적: 최신순으로 나오고, 같은 시각이면 나중에 남은 행이 먼저 오는지 확인한다.
  @Test
  @DisplayName("최신순이고, 같은 시각이면 나중에 남은 행이 먼저 온다")
  void 최신순() {
    Account account = account(9_803_001L);
    AccountTransaction at11 = ledger(account, AccountTransactionType.CHARGE, -1L, 1L, 1L, NOW.plusHours(2));
    AccountTransaction at09 = ledger(account, AccountTransactionType.SIGNUP_BONUS, 2L, 2L, null, NOW);
    AccountTransaction at10 = ledger(account, AccountTransactionType.REFUND, 3L, 3L, 2L, NOW.plusHours(1));
    AccountTransaction sameFirst = ledger(account, AccountTransactionType.CHARGE, -4L, 4L, 3L, NOW.plusHours(3));
    AccountTransaction sameSecond = ledger(account, AccountTransactionType.REFUND, 5L, 5L, 4L, NOW.plusHours(3));

    PageResponse<AccountTransactionResponse> page = accountQueryService.getTransactions(9_803_001L, 0, 10);

    assertThat(page.content())
        .extracting(AccountTransactionResponse::transactionId)
        .containsExactly(
            sameSecond.getId(), sameFirst.getId(), at11.getId(), at10.getId(), at09.getId());
  }

  @Test
  @DisplayName("페이지 번호와 크기대로 잘라서 주고, 전체 건수와 페이지 수를 알려 준다")
  void 페이징() {
    Account account = account(9_804_001L);
    for (int i = 0; i < 25; i++) {
      ledger(account, AccountTransactionType.CHARGE, -1L, 1L, (long) (100 + i), NOW.plusMinutes(i));
    }

    PageResponse<AccountTransactionResponse> third = accountQueryService.getTransactions(9_804_001L, 2, 10);

    assertThat(third.content()).hasSize(5);
    assertThat(third.page()).isEqualTo(2);
    assertThat(third.size()).isEqualTo(10);
    assertThat(third.totalElements()).isEqualTo(25);
    assertThat(third.totalPages()).isEqualTo(3);
  }

  @Test
  @DisplayName("한 페이지 크기가 100을 넘으면 100으로 줄인다")
  void 크기_상한() {
    account(9_805_001L);

    assertThat(accountQueryService.getTransactions(9_805_001L, 0, 1_000).size()).isEqualTo(100);
  }

  @Test
  @DisplayName("거래가 없으면 빈 페이지다")
  void 내역_없음() {
    account(9_806_001L);

    PageResponse<AccountTransactionResponse> page = accountQueryService.getTransactions(9_806_001L, 0, 10);

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
  }

  // 목적: 가입 보너스·충전·환불이 부호와 참조 ID 를 그대로 담아 나오는지 확인한다.
  @Test
  @DisplayName("가입 보너스·충전·환불의 종류, 부호 있는 금액, 거래 후 잔액, 참조 ID 가 그대로 나온다")
  void 필드_매핑() {
    Account account = account(9_807_001L);
    ledger(account, AccountTransactionType.SIGNUP_BONUS, 2_000_000L, 2_000_000L, null, NOW);
    ledger(account, AccountTransactionType.CHARGE, -100_000L, 1_900_000L, 7L, NOW.plusMinutes(1));
    ledger(account, AccountTransactionType.REFUND, 30_000L, 1_930_000L, 8L, NOW.plusMinutes(2));

    List<AccountTransactionResponse> rows = accountQueryService.getTransactions(9_807_001L, 0, 10).content();

    assertThat(rows).hasSize(3);
    AccountTransactionResponse refund = rows.get(0);
    assertThat(refund.type()).isEqualTo(AccountTransactionType.REFUND);
    assertThat(refund.amount()).isEqualTo(30_000L);
    assertThat(refund.balanceAfter()).isEqualTo(1_930_000L);
    assertThat(refund.refKey()).isEqualTo(8L);
    AccountTransactionResponse charge = rows.get(1);
    assertThat(charge.type()).isEqualTo(AccountTransactionType.CHARGE);
    assertThat(charge.amount()).isEqualTo(-100_000L);
    assertThat(charge.refKey()).isEqualTo(7L);
    AccountTransactionResponse bonus = rows.get(2);
    assertThat(bonus.type()).isEqualTo(AccountTransactionType.SIGNUP_BONUS);
    assertThat(bonus.amount()).isEqualTo(2_000_000L);
    assertThat(bonus.refKey()).isNull();
    assertThat(bonus.createdAt()).isEqualTo(NOW);
  }
}
