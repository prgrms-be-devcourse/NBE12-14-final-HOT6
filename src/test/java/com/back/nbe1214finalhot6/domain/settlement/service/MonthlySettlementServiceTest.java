package com.back.nbe1214finalhot6.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlementStatus;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantAccount;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.MerchantWalletService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 테스트 시계는 2026-11-18 09:00 KST 에서 시작하므로 정산할 수 있는 달은 10월 이전이다.
@IntegrationTest
class MonthlySettlementServiceTest {

  private static final Long MERCHANT = 1L;
  private static final Long OTHER_MERCHANT = 2L;
  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
  private static final LocalDate OCTOBER_FIRST = LocalDate.of(2026, 10, 1);

  @Autowired MonthlySettlementService settlementService;
  @Autowired MonthlySettlementRepository monthlyRepository;
  @Autowired DailySettlementRepository dailyRepository;
  @Autowired MerchantWalletService walletService;
  @Autowired MerchantWalletRepository walletRepository;
  @Autowired MerchantAccountRepository accountRepository;
  @Autowired MerchantWalletTransactionRepository transactionRepository;

  @AfterEach
  void cleanUp() {
    transactionRepository.deleteAll();
    monthlyRepository.deleteAll();
    dailyRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
  }

  @Test
  @DisplayName("전월 일 정산 확정액의 합이 지급액이 되고 지갑을 거쳐 계좌로 들어간다")
  void 전월합계_지급() {
    walletService.createFor(MERCHANT);
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 5), 10_000L);
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 20), 5_000L);
    saveDaily(MERCHANT, LocalDate.of(2026, 9, 30), 7_000L);
    saveDaily(MERCHANT, LocalDate.of(2026, 11, 1), 9_000L);

    MonthlySettlementRunResult result = settlementService.settle(OCTOBER);

    MonthlySettlement settlement = find(MERCHANT);
    assertThat(settlement.getPayoutAmount()).isEqualTo(15_000L);
    assertThat(settlement.getStatus()).isEqualTo(MonthlySettlementStatus.PAID);
    assertThat(settlement.getPaidAt()).isNotNull();
    assertThat(result.created()).isEqualTo(1);
    assertThat(result.paid()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    assertThat(walletBalance(MERCHANT)).isZero();
    assertThat(accountBalance(MERCHANT)).isEqualTo(15_000L);
    assertThat(
            transactionRepository
                .findByTypeAndRefKey(
                    MerchantWalletTransactionType.SETTLEMENT_PAYOUT, settlement.getId())
                .orElseThrow()
                .getAmount())
        .isEqualTo(15_000L);
  }

  @Test
  @DisplayName("월 확정액이 0원이면 월 정산 건을 만들지 않는다")
  void 지급액_0원이면_건없음() {
    walletService.createFor(MERCHANT);
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 5), 0L);

    MonthlySettlementRunResult result = settlementService.settle(OCTOBER);

    assertThat(monthlyRepository.count()).isZero();
    assertThat(result.created()).isZero();
    assertThat(transactionRepository.count()).isZero();
  }

  @Test
  @DisplayName("같은 달을 다시 실행해도 월 정산 건과 입금은 한 번만 생긴다")
  void 재실행_중복방지() {
    walletService.createFor(MERCHANT);
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 5), 15_000L);

    MonthlySettlementRunResult first = settlementService.settle(OCTOBER);
    MonthlySettlementRunResult second = settlementService.settle(OCTOBER);

    assertThat(first.created()).isEqualTo(1);
    assertThat(second.created()).isZero();
    assertThat(second.paid()).isZero();
    assertThat(monthlyRepository.count()).isEqualTo(1);
    assertThat(transactionRepository.count()).isEqualTo(2);
    assertThat(accountBalance(MERCHANT)).isEqualTo(15_000L);
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
  @DisplayName("입금이 실패하면 건이 대기로 남고 다음 실행에서 지급된다")
  void 입금실패_대기후_재시도() {
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 5), 15_000L);

    MonthlySettlementRunResult first = settlementService.settle(OCTOBER);

    assertThat(first.failed()).isEqualTo(1);
    assertThat(find(MERCHANT).getStatus()).isEqualTo(MonthlySettlementStatus.PENDING);

    walletService.createFor(MERCHANT);
    MonthlySettlementRunResult second = settlementService.settle(OCTOBER);

    assertThat(second.created()).isZero();
    assertThat(second.paid()).isEqualTo(1);
    assertThat(find(MERCHANT).getStatus()).isEqualTo(MonthlySettlementStatus.PAID);
    assertThat(accountBalance(MERCHANT)).isEqualTo(15_000L);
  }

  @Test
  @DisplayName("한 가맹점의 지급이 실패해도 다른 가맹점은 지급된다")
  void 한곳_실패해도_다른곳_지급() {
    walletService.createFor(MERCHANT);
    saveDaily(OTHER_MERCHANT, LocalDate.of(2026, 10, 5), 20_000L);
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 5), 15_000L);

    MonthlySettlementRunResult result = settlementService.settle(OCTOBER);

    assertThat(result.created()).isEqualTo(2);
    assertThat(result.paid()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(find(MERCHANT).getStatus()).isEqualTo(MonthlySettlementStatus.PAID);
    assertThat(find(OTHER_MERCHANT).getStatus()).isEqualTo(MonthlySettlementStatus.PENDING);
  }

  @Test
  @DisplayName("입금 뒤 출금이 실패하면 지갑 잔액으로 남고 재시도하면 출금만 수행된다")
  void 출금실패_잔액남고_재시도시_출금만() {
    walletRepository.save(MerchantWallet.create(MERCHANT, LocalDateTime.now()));
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 5), 15_000L);

    MonthlySettlementRunResult first = settlementService.settle(OCTOBER);

    assertThat(first.failed()).isEqualTo(1);
    assertThat(walletBalance(MERCHANT)).isEqualTo(15_000L);
    assertThat(find(MERCHANT).getStatus()).isEqualTo(MonthlySettlementStatus.PENDING);

    accountRepository.save(MerchantAccount.create(MERCHANT, LocalDateTime.now()));
    MonthlySettlementRunResult second = settlementService.settle(OCTOBER);

    assertThat(second.paid()).isEqualTo(1);
    assertThat(find(MERCHANT).getStatus()).isEqualTo(MonthlySettlementStatus.PAID);
    assertThat(walletBalance(MERCHANT)).isZero();
    assertThat(accountBalance(MERCHANT)).isEqualTo(15_000L);
    assertThat(transactionRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("대기 건 지급만 호출하면 새 월 정산 건은 만들지 않고 밀린 건만 지급한다")
  void 대기건_지급만_호출() {
    saveDaily(MERCHANT, LocalDate.of(2026, 10, 5), 15_000L);
    settlementService.settle(OCTOBER);
    walletService.createFor(MERCHANT);
    saveDaily(OTHER_MERCHANT, LocalDate.of(2026, 10, 6), 20_000L);

    MonthlySettlementRunResult result = settlementService.payPending(OCTOBER);

    assertThat(result.created()).isZero();
    assertThat(result.paid()).isEqualTo(1);
    assertThat(monthlyRepository.count()).isEqualTo(1);
    assertThat(find(MERCHANT).getStatus()).isEqualTo(MonthlySettlementStatus.PAID);
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

  private long walletBalance(Long merchantId) {
    return walletRepository.findByMerchantId(merchantId).orElseThrow().getBalance();
  }

  private long accountBalance(Long merchantId) {
    return accountRepository.findByMerchantId(merchantId).orElseThrow().getBalance();
  }
}
