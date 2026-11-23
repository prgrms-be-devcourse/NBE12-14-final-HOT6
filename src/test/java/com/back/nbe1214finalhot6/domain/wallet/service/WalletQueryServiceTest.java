package com.back.nbe1214finalhot6.domain.wallet.service;

import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.CHARGE;
import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.REFUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.wallet.dto.WalletResponse;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletTransactionRepository;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.entity.OperatorSetting;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 지갑 조회. 시계는 2026-11-18 09:00 KST에서 시작하므로 "이번 달"은 11월. 한도 기본값은 보유 200만원, 월 충전 100만원. */
@IntegrationTest
class WalletQueryServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 9, 0);
  private static final Long USER_ID = 9_601L;

  @Autowired WalletQueryService walletQueryService;
  @Autowired ChargeLimitService chargeLimitService;
  @Autowired WalletService walletService;
  @Autowired WalletRepository walletRepository;
  @Autowired WalletTransactionRepository walletTransactionRepository;
  @Autowired OperatorSettingRepository operatorSettingRepository;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired MutableClock clock;

  // 테스트가 DB에 남긴 행 정리(원장 → 지갑 → 설정 순)
  @AfterEach
  void cleanUp() {
    walletTransactionRepository.deleteAll();
    walletRepository.deleteAll();
    operatorSettingRepository.deleteAll();
  }

  @Test
  @DisplayName("데브머니·캐시백 잔액과 한도 현황을 돌려준다")
  void returnsBalancesAndLimits() {
    wallet(USER_ID, 150_000L, 500L);

    WalletResponse response = walletQueryService.getMyWallet(USER_ID);

    assertThat(response.devMoney()).isEqualTo(150_000L);
    assertThat(response.cashbackBalance()).isEqualTo(500L);
    assertThat(response.availableCashback()).isEqualTo(500L);
    assertThat(response.holdingLimit()).isEqualTo(2_000_000L);
    assertThat(response.monthlyChargeLimit()).isEqualTo(1_000_000L);
    assertThat(response.chargedThisMonth()).isZero();
    // 보유 한도 기준 185만원, 월 한도 기준 100만원 중 작은 쪽
    assertThat(response.chargeableAmount()).isEqualTo(1_000_000L);
  }

  @Test
  @DisplayName("캐시백 잔액이 음수면 잔액은 음수 그대로, 쓸 수 있는 캐시백은 0이다")
  void negativeCashback_availableIsZero() {
    Wallet wallet = Wallet.create(USER_ID, NOW);
    wallet.credit(100L, true, NOW);
    wallet.debit(300L, true, true, NOW); // 회수로 -200원
    walletRepository.save(wallet);

    WalletResponse response = walletQueryService.getMyWallet(USER_ID);

    assertThat(response.cashbackBalance()).isEqualTo(-200L);
    assertThat(response.availableCashback()).isZero();
  }

  @Test
  @DisplayName("이번 달 충전 누적은 월 경계(KST)에 맞게 계산한다")
  void chargedThisMonth_monthBoundary() {
    Wallet wallet = wallet(USER_ID, 0L, 0L);
    ledger(wallet, CHARGE, 400_000L, LocalDateTime.of(2026, 10, 31, 23, 59, 59)); // 지난달, 제외
    ledger(wallet, CHARGE, 600_000L, LocalDateTime.of(2026, 11, 1, 0, 0, 0)); // 이번 달, 포함

    WalletResponse response = walletQueryService.getMyWallet(USER_ID);

    assertThat(response.chargedThisMonth()).isEqualTo(600_000L);
    assertThat(response.chargeableAmount()).isEqualTo(400_000L);
  }

  @Test
  @DisplayName("달이 바뀌면 이번 달 충전 누적이 0부터 다시 시작한다")
  void chargedThisMonth_resetsOnNewMonth() {
    Wallet wallet = wallet(USER_ID, 0L, 0L);
    ledger(wallet, CHARGE, 1_000_000L, LocalDateTime.of(2026, 11, 5, 10, 0));

    WalletResponse november = walletQueryService.getMyWallet(USER_ID);
    clock.set(Instant.parse("2026-11-30T15:00:00Z")); // 2026-12-01 00:00 KST
    WalletResponse december = walletQueryService.getMyWallet(USER_ID);

    assertThat(november.chargedThisMonth()).isEqualTo(1_000_000L);
    assertThat(november.chargeableAmount()).isZero();
    assertThat(december.chargedThisMonth()).isZero();
    assertThat(december.chargeableAmount()).isEqualTo(1_000_000L);
  }

  @Test
  @DisplayName("환불(REFUND)이 있어도 이번 달 충전 누적은 줄지 않는다")
  void refundDoesNotReduceChargedThisMonth() {
    Wallet wallet = wallet(USER_ID, 0L, 0L);
    ledger(wallet, CHARGE, 900_000L, LocalDateTime.of(2026, 11, 5, 10, 0));
    ledger(wallet, REFUND, -500_000L, LocalDateTime.of(2026, 11, 10, 10, 0));

    WalletResponse response = walletQueryService.getMyWallet(USER_ID);

    assertThat(response.chargedThisMonth()).isEqualTo(900_000L);
    assertThat(response.chargeableAmount()).isEqualTo(100_000L);
  }

  @Test
  @DisplayName("남은 충전 가능액은 보유 한도와 월 충전 한도 중 더 빡빡한 쪽이다")
  void chargeableAmount_tighterLimit() {
    wallet(USER_ID, 1_800_000L, 0L); // 보유 한도 기준 20만원
    assertThat(walletQueryService.getMyWallet(USER_ID).chargeableAmount()).isEqualTo(200_000L);

    walletRepository.deleteAll();
    Wallet monthly = wallet(USER_ID, 0L, 0L);
    ledger(monthly, CHARGE, 700_000L, LocalDateTime.of(2026, 11, 5, 10, 0)); // 월 한도 기준 30만원
    assertThat(walletQueryService.getMyWallet(USER_ID).chargeableAmount()).isEqualTo(300_000L);
  }

  @Test
  @DisplayName("한도를 낮춰 이미 넘은 상태면 남은 충전 가능액은 음수가 아니라 0이다")
  void overLimit_chargeableIsZero() {
    operatorSettingRepository.save(
        OperatorSetting.create(SettingKey.WALLET_HOLDING_LIMIT, 1_000_000L, null, NOW));
    wallet(USER_ID, 1_500_000L, 0L);

    WalletResponse response = walletQueryService.getMyWallet(USER_ID);

    assertThat(response.holdingLimit()).isEqualTo(1_000_000L);
    assertThat(response.devMoney()).isEqualTo(1_500_000L); // 기존 잔액은 그대로
    assertThat(response.chargeableAmount()).isZero();
  }

  @Test
  @DisplayName("운영자가 바꾼 한도가 응답에 반영된다")
  void changedLimits_reflected() {
    operatorSettingRepository.save(
        OperatorSetting.create(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT, 700_000L, null, NOW));
    wallet(USER_ID, 0L, 0L);

    WalletResponse response = walletQueryService.getMyWallet(USER_ID);

    assertThat(response.monthlyChargeLimit()).isEqualTo(700_000L);
    assertThat(response.chargeableAmount()).isEqualTo(700_000L);
  }

  @Test
  @DisplayName("다른 지갑의 충전은 이번 달 충전 누적에 섞이지 않는다")
  void otherWalletIgnored() {
    wallet(USER_ID, 0L, 0L);
    Wallet other = wallet(USER_ID + 1, 0L, 0L);
    ledger(other, CHARGE, 1_000_000L, LocalDateTime.of(2026, 11, 5, 10, 0));

    WalletResponse response = walletQueryService.getMyWallet(USER_ID);

    assertThat(response.chargedThisMonth()).isZero();
  }

  @Test
  @DisplayName("없는 지갑은 WAL-001, 탈퇴한 지갑은 WAL-002로 거절한다")
  void notFoundAndWithdrawn() {
    assertFails(() -> walletQueryService.getMyWallet(USER_ID), WalletErrorCode.WALLET_NOT_FOUND);

    Wallet wallet = wallet(USER_ID, 0L, 0L);
    wallet.withdraw(NOW);
    walletRepository.save(wallet);
    assertFails(() -> walletQueryService.getMyWallet(USER_ID), WalletErrorCode.WALLET_WITHDRAWN);
  }

  @Test
  @DisplayName("같은 요청에서 먼저 읽어 둔 낡은 지갑 엔티티가 있어도 최신 잔액을 돌려준다")
  void staleEntity_doesNotAffectQuery() {
    Wallet wallet = wallet(USER_ID, 100_000L, 0L);
    long[] devMoney = new long[1];

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              walletRepository.findById(wallet.getId()); // 먼저 일반 조회(낡은 객체가 남음)
              // 그 사이 다른 트랜잭션이 90만원을 충전하고 커밋
              inNewTransaction(() -> walletService.credit(wallet.getId(), 900_000L, CHARGE, 1L));
              devMoney[0] = walletQueryService.getMyWallet(USER_ID).devMoney();
            });

    assertThat(devMoney[0]).isEqualTo(1_000_000L);
  }

  @Test
  @DisplayName("남은 충전 가능액은 충전 검사가 허용하는 최대 금액과 같다")
  void chargeableAmount_matchesChargeCheck() {
    // 보유 한도가 더 빡빡한 경우: 잔액 150만원, 이번 달 30만원 충전 → 50만원
    Wallet holding = wallet(USER_ID, 1_500_000L, 0L);
    ledger(holding, CHARGE, 300_000L, LocalDateTime.of(2026, 11, 5, 10, 0));
    assertThat(walletQueryService.getMyWallet(USER_ID).chargeableAmount()).isEqualTo(500_000L);
    assertThatCode(() -> check(holding.getId(), 500_000L)).doesNotThrowAnyException();
    assertFails(() -> check(holding.getId(), 500_001L), WalletErrorCode.HOLDING_LIMIT_EXCEEDED);

    // 월 한도가 더 빡빡한 경우: 잔액 0, 이번 달 60만원 충전 → 40만원
    walletTransactionRepository.deleteAll();
    walletRepository.deleteAll();
    Wallet monthly = wallet(USER_ID, 0L, 0L);
    ledger(monthly, CHARGE, 600_000L, LocalDateTime.of(2026, 11, 5, 10, 0));
    assertThat(walletQueryService.getMyWallet(USER_ID).chargeableAmount()).isEqualTo(400_000L);
    assertThatCode(() -> check(monthly.getId(), 400_000L)).doesNotThrowAnyException();
    assertFails(
        () -> check(monthly.getId(), 400_001L), WalletErrorCode.MONTHLY_CHARGE_LIMIT_EXCEEDED);
  }

  // 데브머니·캐시백 잔액을 가진 지갑 저장
  private Wallet wallet(Long userId, long devMoney, long cashback) {
    Wallet wallet = Wallet.create(userId, NOW);
    if (devMoney > 0) {
      wallet.credit(devMoney, false, NOW);
    }
    if (cashback > 0) {
      wallet.credit(cashback, true, NOW);
    }
    return walletRepository.save(wallet);
  }

  // 이번 달 충전 누적 계산에 쓰는 원장 행. type, 금액, 시각, 지갑만 보므로 나머지는 단순하게 채움
  private void ledger(Wallet wallet, WalletTransactionType type, long amount, LocalDateTime at) {
    walletTransactionRepository.save(
        WalletTransaction.builder()
            .walletId(wallet.getId())
            .type(type)
            .amount(amount)
            .balanceAfter(0L)
            .createdAt(at)
            .build());
  }

  // 충전 검사는 호출한 쪽 트랜잭션이 필수라서 트랜잭션 안에서 호출함
  private void check(Long walletId, long amount) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> chargeLimitService.assertChargeable(walletId, amount, NOW));
  }

  // 별도 트랜잭션으로 실행하고 커밋함. 다른 요청이 끼어든 상황을 만들 때 사용
  private void inNewTransaction(Runnable action) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.executeWithoutResult(status -> action.run());
  }

  private void assertFails(ThrowingCallable call, WalletErrorCode expected) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }
}
