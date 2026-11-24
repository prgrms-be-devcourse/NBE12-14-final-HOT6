package com.back.nbe1214finalhot6.domain.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationResult;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationStatus;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.ReconciliationResultRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.entity.Refund;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.ChargeRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.RefundRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.ChargeService;
import com.back.nbe1214finalhot6.domain.wallet.service.RefundService;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletOpeningService;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

// 테스트 시계는 2026-11-18 09:00 KST 에서 시작한다. 데이터를 만든 뒤 시계를 하루 넘겨 11/18 을 대사한다.
// 잔액 대사는 전체를 보므로, 다른 테스트가 남긴 행이 결과에 섞이지 않게 앞뒤로 지운다.
@IntegrationTest
class LedgerReconciliationCheckTest {

  private static final Long USER = 1L;
  private static final LocalDate DAY = LocalDate.of(2026, 11, 18);

  @Autowired ReconciliationService reconciliationService;
  @Autowired ReconciliationResultRepository resultRepository;
  @Autowired WalletOpeningService openingService;
  @Autowired ChargeService chargeService;
  @Autowired RefundService refundService;
  @Autowired WalletService walletService;
  @Autowired WalletRepository walletRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired WalletTransactionRepository walletTransactionRepository;
  @Autowired AccountTransactionRepository accountTransactionRepository;
  @Autowired ChargeRepository chargeRepository;
  @Autowired RefundRepository refundRepository;
  @Autowired CashbackEarningRepository earningRepository;
  @Autowired TransactionTemplate transactionTemplate;
  @Autowired MutableClock clock;

  private Long walletId;

  @BeforeEach
  void setUp() {
    cleanUp();
    openingService.open(USER);
    walletId = walletRepository.findByUserId(USER).orElseThrow().getId();
  }

  @AfterEach
  void cleanUp() {
    resultRepository.deleteAll();
    earningRepository.deleteAll();
    walletTransactionRepository.deleteAll();
    accountTransactionRepository.deleteAll();
    chargeRepository.deleteAll();
    refundRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
  }

  @Test
  @DisplayName("충전·환불·적립 지급이 정상이면 잔액·원장 대사가 모두 일치한다")
  void 정상_모두일치() {
    chargeService.charge(walletId, 100_000L, "charge-1");
    refundService.refund(walletId, 30_000L, "refund-1");
    paidEarning(1L, 500L);

    List<ReconciliationResult> results = runNextDay();

    assertThat(results)
        .filteredOn(r -> LEDGER_TYPES.contains(r.getType()))
        .hasSize(6)
        .allMatch(r -> r.getStatus() == ReconciliationStatus.MATCHED);
    assertThat(result(results, ReconciliationType.CHARGE_TRANSFER).getCheckedCount()).isEqualTo(1);
    assertThat(result(results, ReconciliationType.REFUND_TRANSFER).getCheckedCount()).isEqualTo(1);
    assertThat(result(results, ReconciliationType.CASHBACK_EARNING).getCheckedCount())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("원장 없이 데브머니 잔액만 바뀌면 데브머니 잔액 대사에서 불일치로 잡힌다")
  void 데브머니잔액_불일치() {
    chargeService.charge(walletId, 100_000L, "charge-1");
    Wallet wallet = walletRepository.findById(walletId).orElseThrow();
    wallet.credit(1_000L, false, LocalDateTime.now(clock));
    walletRepository.save(wallet);

    List<ReconciliationResult> results = runNextDay();

    assertMismatch(results, ReconciliationType.DEVMONEY_BALANCE, "walletId=" + walletId);
    assertThat(result(results, ReconciliationType.CASHBACK_BALANCE).getStatus())
        .isEqualTo(ReconciliationStatus.MATCHED);
  }

  @Test
  @DisplayName("원장 없이 캐시백 잔액만 바뀌면 캐시백 잔액 대사에서 불일치로 잡힌다")
  void 캐시백잔액_불일치() {
    Wallet wallet = walletRepository.findById(walletId).orElseThrow();
    wallet.credit(500L, true, LocalDateTime.now(clock));
    walletRepository.save(wallet);

    List<ReconciliationResult> results = runNextDay();

    assertMismatch(results, ReconciliationType.CASHBACK_BALANCE, "walletId=" + walletId);
    assertThat(result(results, ReconciliationType.DEVMONEY_BALANCE).getStatus())
        .isEqualTo(ReconciliationStatus.MATCHED);
  }

  @Test
  @DisplayName("원장 없이 계좌 잔액만 바뀌면 계좌 잔액 대사에서 불일치로 잡힌다")
  void 계좌잔액_불일치() {
    Account account = accountRepository.findByUserId(USER).orElseThrow();
    account.credit(1_000L);
    accountRepository.save(account);

    List<ReconciliationResult> results = runNextDay();

    assertMismatch(results, ReconciliationType.ACCOUNT_BALANCE, "accountId=" + account.getId());
  }

  @Test
  @DisplayName("충전의 계좌 원장이 없으면 충전 대사에서 불일치로 잡힌다")
  void 충전원장_누락() {
    Long chargeId = chargeService.charge(walletId, 100_000L, "charge-1").chargeId();
    accountTransactionRepository.deleteAll(
        accountTransactionRepository.findAll().stream()
            .filter(t -> t.getType() == AccountTransactionType.CHARGE)
            .toList());

    List<ReconciliationResult> results = runNextDay();

    assertMismatch(results, ReconciliationType.CHARGE_TRANSFER, "chargeId=" + chargeId);
  }

  @Test
  @DisplayName("환불 기록만 있고 원장이 없으면 환불 대사에서 불일치로 잡힌다")
  void 환불원장_누락() {
    Long refundId =
        refundRepository
            .save(Refund.create(walletId, 10_000L, "refund-1", LocalDateTime.now(clock)))
            .getId();

    List<ReconciliationResult> results = runNextDay();

    assertMismatch(results, ReconciliationType.REFUND_TRANSFER, "refundId=" + refundId);
  }

  @Test
  @DisplayName("지급 완료인데 적립 입금 원장이 없으면 적립 건 대사에서 불일치로 잡힌다")
  void 지급완료_원장없음() {
    Long earningId = pendingEarning(1L, 500L);
    markPaid(earningId);

    List<ReconciliationResult> results = runNextDay();

    assertMismatch(results, ReconciliationType.CASHBACK_EARNING, "earningId=" + earningId);
  }

  @Test
  @DisplayName("적립 예정 건인데 적립 입금 원장이 있으면 적립 건 대사에서 불일치로 잡힌다")
  void 대기건_원장있음() {
    Long earningId = pendingEarning(1L, 500L);
    walletService.credit(walletId, 500L, WalletTransactionType.CASHBACK_EARN, earningId);

    List<ReconciliationResult> results = runNextDay();

    assertMismatch(results, ReconciliationType.CASHBACK_EARNING, "earningId=" + earningId);
  }

  @Test
  @DisplayName("지급한 캐시백을 쓴 뒤 회수해 잔액이 음수가 되어도 캐시백 대사는 일치한다")
  void 회수_음수잔액_일치() {
    Long earningId = paidEarning(1L, 500L);
    walletService.debitPayment(walletId, 0L, 500L, 100L);
    walletService.debit(walletId, 500L, WalletTransactionType.CASHBACK_CLAWBACK, earningId, true);
    transactionTemplate.executeWithoutResult(
        status ->
            earningRepository.clawBackIfPaid(
                earningId,
                LocalDateTime.now(clock),
                EarningStatus.PAID,
                EarningStatus.CLAWED_BACK));

    List<ReconciliationResult> results = runNextDay();

    assertThat(walletRepository.findById(walletId).orElseThrow().getCashbackBalance())
        .isEqualTo(-500L);
    assertThat(result(results, ReconciliationType.CASHBACK_BALANCE).getStatus())
        .isEqualTo(ReconciliationStatus.MATCHED);
    assertThat(result(results, ReconciliationType.CASHBACK_EARNING).getStatus())
        .isEqualTo(ReconciliationStatus.MATCHED);
  }

  private static final List<ReconciliationType> LEDGER_TYPES =
      List.of(
          ReconciliationType.ACCOUNT_BALANCE,
          ReconciliationType.DEVMONEY_BALANCE,
          ReconciliationType.CASHBACK_BALANCE,
          ReconciliationType.CASHBACK_EARNING,
          ReconciliationType.CHARGE_TRANSFER,
          ReconciliationType.REFUND_TRANSFER);

  private List<ReconciliationResult> runNextDay() {
    clock.advance(Duration.ofDays(1));
    return reconciliationService.run(DAY);
  }

  private ReconciliationResult result(List<ReconciliationResult> results, ReconciliationType type) {
    return results.stream().filter(r -> r.getType() == type).findFirst().orElseThrow();
  }

  private void assertMismatch(
      List<ReconciliationResult> results, ReconciliationType type, String ref) {
    ReconciliationResult result = result(results, type);
    assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
    assertThat(result.getMismatchRefs()).isEqualTo(ref);
  }

  private Long pendingEarning(Long paymentId, long amount) {
    LocalDateTime now = LocalDateTime.now(clock);
    return earningRepository
        .save(
            CashbackEarning.pending(
                paymentId,
                walletId,
                amount * 10,
                now,
                new BigDecimal("0.1").setScale(CashbackPolicy.RATE_SCALE),
                null,
                amount,
                now,
                LocalDate.of(2026, 11, 1),
                now))
        .getId();
  }

  private Long paidEarning(Long paymentId, long amount) {
    Long earningId = pendingEarning(paymentId, amount);
    walletService.credit(walletId, amount, WalletTransactionType.CASHBACK_EARN, earningId);
    markPaid(earningId);
    return earningId;
  }

  private void markPaid(Long earningId) {
    transactionTemplate.executeWithoutResult(
        status ->
            earningRepository.markPaidIfPending(
                earningId, LocalDateTime.now(clock), EarningStatus.PENDING, EarningStatus.PAID));
  }
}
