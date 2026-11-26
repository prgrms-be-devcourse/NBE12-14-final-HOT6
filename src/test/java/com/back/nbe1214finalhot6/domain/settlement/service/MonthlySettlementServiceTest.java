package com.back.nbe1214finalhot6.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlementStatus;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 테스트 시계는 2026-11-18 09:00 KST 에서 시작하므로 정산할 수 있는 달은 10월 이전이다.
// 월 정산금은 가맹점 점주의 모의계좌로 들어가므로, 점주가 있는 가맹점을 만들고 점주 계좌는 테스트마다 필요할 때 연다.
@IntegrationTest
class MonthlySettlementServiceTest {

  private static final Long OWNER = 8_101L;
  private static final Long OTHER_OWNER = 8_102L;
  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
  private static final LocalDate OCTOBER_FIRST = LocalDate.of(2026, 10, 1);

  @Autowired MonthlySettlementService settlementService;
  @Autowired MonthlySettlementRepository monthlyRepository;
  @Autowired DailySettlementRepository dailyRepository;
  @Autowired MerchantRepository merchantRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired AccountTransactionRepository accountTransactionRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  private Long merchant;
  private Long otherMerchant;

  @BeforeEach
  void saveMerchants() {
    merchant = merchantRepository.save(Merchant.create(OWNER, "정산 가맹점", "인천광역시 남동구 1", true)).getId();
    otherMerchant =
        merchantRepository.save(Merchant.create(OTHER_OWNER, "다른 가맹점", "인천광역시 남동구 2", true)).getId();
  }

  @AfterEach
  void cleanUp() {
    for (Long owner : new Long[] {OWNER, OTHER_OWNER}) {
      jdbcTemplate.update(
          "delete from account_transaction where account_id in (select id from account where user_id = ?)",
          owner);
      jdbcTemplate.update("delete from account where user_id = ?", owner);
    }
    monthlyRepository.deleteAll();
    dailyRepository.deleteAll();
    merchantRepository.deleteAllById(List.of(merchant, otherMerchant));
  }

  @Test
  @DisplayName("전월 일 정산 확정액의 합이 지급액이 되고 점주의 모의계좌로 들어간다")
  void 전월합계_지급() {
    openAccount(OWNER);
    saveDaily(merchant, LocalDate.of(2026, 10, 5), 10_000L);
    saveDaily(merchant, LocalDate.of(2026, 10, 20), 5_000L);
    saveDaily(merchant, LocalDate.of(2026, 9, 30), 7_000L);
    saveDaily(merchant, LocalDate.of(2026, 11, 1), 9_000L);

    MonthlySettlementRunResult result = settlementService.settle(OCTOBER);

    MonthlySettlement settlement = find(merchant);
    assertThat(settlement.getPayoutAmount()).isEqualTo(15_000L);
    assertThat(settlement.getStatus()).isEqualTo(MonthlySettlementStatus.PAID);
    assertThat(settlement.getPaidAt()).isNotNull();
    assertThat(result.created()).isEqualTo(1);
    assertThat(result.paid()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    assertThat(accountBalance(OWNER)).isEqualTo(15_000L);
    assertThat(
            accountTransactionRepository
                .findByTypeAndRefKey(AccountTransactionType.SETTLEMENT_PAYOUT, settlement.getId())
                .orElseThrow()
                .getAmount())
        .isEqualTo(15_000L);
  }

  @Test
  @DisplayName("월 확정액이 0원이면 월 정산 건을 만들지 않고 계좌에도 기록이 없다")
  void 지급액_0원이면_건없음() {
    openAccount(OWNER);
    saveDaily(merchant, LocalDate.of(2026, 10, 5), 0L);

    MonthlySettlementRunResult result = settlementService.settle(OCTOBER);

    assertThat(monthlyRepository.count()).isZero();
    assertThat(result.created()).isZero();
    assertThat(ledgerCount(OWNER)).isZero();
  }

  @Test
  @DisplayName("같은 달을 다시 실행해도 월 정산 건과 계좌 입금은 한 번만 생긴다")
  void 재실행_중복방지() {
    openAccount(OWNER);
    saveDaily(merchant, LocalDate.of(2026, 10, 5), 15_000L);

    MonthlySettlementRunResult first = settlementService.settle(OCTOBER);
    MonthlySettlementRunResult second = settlementService.settle(OCTOBER);

    assertThat(first.created()).isEqualTo(1);
    assertThat(second.created()).isZero();
    assertThat(second.paid()).isZero();
    assertThat(monthlyRepository.count()).isEqualTo(1);
    assertThat(ledgerCount(OWNER)).isEqualTo(1);
    assertThat(accountBalance(OWNER)).isEqualTo(15_000L);
  }

  @Test
  @DisplayName("이번 달 이후는 월 정산할 수 없다")
  void 이번달_이후_거절() {
    assertThatThrownBy(() -> settlementService.settle(YearMonth.of(2026, 11)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_MONTH_NOT_ALLOWED));
  }

  @Test
  @DisplayName("점주 계좌가 없어 지급이 실패하면 건이 대기로 남고 다음 실행에서 지급된다")
  void 지급실패_대기후_재시도() {
    saveDaily(merchant, LocalDate.of(2026, 10, 5), 15_000L);

    MonthlySettlementRunResult first = settlementService.settle(OCTOBER);

    assertThat(first.failed()).isEqualTo(1);
    assertThat(find(merchant).getStatus()).isEqualTo(MonthlySettlementStatus.PENDING);

    openAccount(OWNER);
    MonthlySettlementRunResult second = settlementService.settle(OCTOBER);

    assertThat(second.created()).isZero();
    assertThat(second.paid()).isEqualTo(1);
    assertThat(find(merchant).getStatus()).isEqualTo(MonthlySettlementStatus.PAID);
    assertThat(accountBalance(OWNER)).isEqualTo(15_000L);
  }

  @Test
  @DisplayName("한 가맹점의 지급이 실패해도 다른 가맹점은 지급된다")
  void 한곳_실패해도_다른곳_지급() {
    openAccount(OWNER);
    saveDaily(otherMerchant, LocalDate.of(2026, 10, 5), 20_000L);
    saveDaily(merchant, LocalDate.of(2026, 10, 5), 15_000L);

    MonthlySettlementRunResult result = settlementService.settle(OCTOBER);

    assertThat(result.created()).isEqualTo(2);
    assertThat(result.paid()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(find(merchant).getStatus()).isEqualTo(MonthlySettlementStatus.PAID);
    assertThat(find(otherMerchant).getStatus()).isEqualTo(MonthlySettlementStatus.PENDING);
  }

  @Test
  @DisplayName("점주가 없는 가맹점은 받을 계좌가 없어 지급하지 못하고 건이 대기로 남는다")
  void 점주없는_가맹점_지급실패() {
    Long noOwner =
        merchantRepository.save(Merchant.create(null, "공공데이터 가맹점", "인천광역시 남동구 3", true)).getId();
    try {
      saveDaily(noOwner, LocalDate.of(2026, 10, 5), 15_000L);

      MonthlySettlementRunResult result = settlementService.settle(OCTOBER);

      assertThat(result.failed()).isEqualTo(1);
      assertThat(find(noOwner).getStatus()).isEqualTo(MonthlySettlementStatus.PENDING);
    } finally {
      monthlyRepository.deleteAll();
      merchantRepository.deleteById(noOwner);
    }
  }

  @Test
  @DisplayName("대기 건 지급만 호출하면 새 월 정산 건은 만들지 않고 밀린 건만 지급한다")
  void 대기건_지급만_호출() {
    saveDaily(merchant, LocalDate.of(2026, 10, 5), 15_000L);
    settlementService.settle(OCTOBER);
    openAccount(OWNER);
    saveDaily(otherMerchant, LocalDate.of(2026, 10, 6), 20_000L);

    MonthlySettlementRunResult result = settlementService.payPending(OCTOBER);

    assertThat(result.created()).isZero();
    assertThat(result.paid()).isEqualTo(1);
    assertThat(monthlyRepository.count()).isEqualTo(1);
    assertThat(find(merchant).getStatus()).isEqualTo(MonthlySettlementStatus.PAID);
  }

  private void openAccount(Long ownerId) {
    accountRepository.save(Account.createEmpty(ownerId, LocalDateTime.now()));
  }

  private void saveDaily(Long merchantId, LocalDate date, long paymentTotal) {
    dailyRepository.save(
        DailySettlement.create(merchantId, date, paymentTotal, 0L, 0L, date.atTime(1, 0)));
  }

  private MonthlySettlement find(Long merchantId) {
    return monthlyRepository.findAll().stream()
        .filter(
            s ->
                s.getMerchantId().equals(merchantId)
                    && s.getSettlementMonth().equals(OCTOBER_FIRST))
        .findFirst()
        .orElseThrow();
  }

  private long accountBalance(Long ownerId) {
    return accountRepository.findByUserId(ownerId).orElseThrow().getBalance();
  }

  private long ledgerCount(Long ownerId) {
    Long accountId = accountRepository.findByUserId(ownerId).orElseThrow().getId();
    return jdbcTemplate.queryForObject(
        "select count(*) from account_transaction where account_id = ?", Long.class, accountId);
  }
}
