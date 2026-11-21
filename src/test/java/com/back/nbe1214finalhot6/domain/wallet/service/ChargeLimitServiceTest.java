package com.back.nbe1214finalhot6.domain.wallet.service;

import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.CASHBACK_EARN;
import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.CHARGE;
import static com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType.REFUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
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
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 보유 한도(기본 200만원)와 월 충전 한도(기본 100만원) 검사를 확인함. 월은 한국 시간 달력월. */
@IntegrationTest
class ChargeLimitServiceTest {

  // 이번 달 중간. 월 누적은 이 시각이 속한 11월로 계산함
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 9, 0);

  @Autowired ChargeLimitService chargeLimitService;
  @Autowired WalletRepository walletRepository;
  @Autowired WalletTransactionRepository walletTransactionRepository;
  @Autowired OperatorSettingRepository operatorSettingRepository;
  @Autowired WalletService walletService;
  @Autowired PlatformTransactionManager transactionManager;

  // 테스트가 DB에 남긴 행 정리(원장 → 지갑 → 설정 순)
  @AfterEach
  void cleanUp() {
    walletTransactionRepository.deleteAll();
    walletRepository.deleteAll();
    operatorSettingRepository.deleteAll();
  }

  @Test
  @DisplayName("보유 한도는 딱 한도와 같으면 허용하고 1원이라도 넘으면 WAL-006으로 거절한다")
  void holdingLimit_boundary() {
    Wallet wallet = wallet(1, 1_500_000L, 0L);

    assertThatCode(() -> check(wallet.getId(), 500_000L, NOW))
        .doesNotThrowAnyException();
    assertFails(
        () -> check(wallet.getId(), 500_001L, NOW),
        WalletErrorCode.HOLDING_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("보유 한도는 데브머니만 보고 캐시백 잔액은 포함하지 않는다")
  void holdingLimit_ignoresCashbackBalance() {
    // 캐시백 300만원이 보유 한도에 포함된다면 100만원 충전도 거절됨(월 한도는 100만원이라 딱 맞음)
    Wallet wallet = wallet(1, 0L, 3_000_000L);

    assertThatCode(() -> check(wallet.getId(), 1_000_000L, NOW))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("월 충전 한도는 이번 달 충전 합계로 계산하며 딱 한도와 같으면 허용한다")
  void monthlyLimit_boundary() {
    Wallet wallet = wallet(1, 0L, 0L);
    ledger(wallet, CHARGE, 600_000L, LocalDateTime.of(2026, 11, 5, 10, 0));
    ledger(wallet, CHARGE, 300_000L, LocalDateTime.of(2026, 11, 10, 10, 0));

    assertThatCode(() -> check(wallet.getId(), 100_000L, NOW))
        .doesNotThrowAnyException();
    assertFails(
        () -> check(wallet.getId(), 100_001L, NOW),
        WalletErrorCode.MONTHLY_CHARGE_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("달이 바뀌면(KST) 월 누적이 0에서 다시 시작한다")
  void monthlyLimit_resetsOnNewMonth() {
    Wallet wallet = wallet(1, 0L, 0L);
    ledger(wallet, CHARGE, 1_000_000L, LocalDateTime.of(2026, 11, 30, 23, 59, 59));

    // 같은 달의 마지막 순간에는 한도가 찬 상태
    assertFails(
        () ->
            check(wallet.getId(), 1L, LocalDateTime.of(2026, 11, 30, 23, 59, 59)),
        WalletErrorCode.MONTHLY_CHARGE_LIMIT_EXCEEDED);
    // 12월 1일 0시부터는 새 달
    assertThatCode(
            () ->
                check(wallet.getId(), 1_000_000L, LocalDateTime.of(2026, 12, 1, 0, 0)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("월 경계: 이번 달 1일 0시 거래는 포함하고 지난달 말일 마지막 순간 거래는 제외한다")
  void monthlyLimit_monthBoundary() {
    Wallet wallet = wallet(1, 0L, 0L);
    ledger(wallet, CHARGE, 400_000L, LocalDateTime.of(2026, 10, 31, 23, 59, 59)); // 지난달, 제외
    ledger(wallet, CHARGE, 600_000L, LocalDateTime.of(2026, 11, 1, 0, 0, 0)); // 이번 달, 포함

    assertThatCode(() -> check(wallet.getId(), 400_000L, NOW))
        .doesNotThrowAnyException();
    assertFails(
        () -> check(wallet.getId(), 400_001L, NOW),
        WalletErrorCode.MONTHLY_CHARGE_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("환불(REFUND)이 있어도 월 충전 한도는 복원되지 않는다")
  void monthlyLimit_refundDoesNotRestore() {
    Wallet wallet = wallet(1, 0L, 0L);
    ledger(wallet, CHARGE, 900_000L, LocalDateTime.of(2026, 11, 5, 10, 0));
    ledger(wallet, REFUND, -500_000L, LocalDateTime.of(2026, 11, 10, 10, 0));

    assertThatCode(() -> check(wallet.getId(), 100_000L, NOW))
        .doesNotThrowAnyException();
    assertFails(
        () -> check(wallet.getId(), 100_001L, NOW),
        WalletErrorCode.MONTHLY_CHARGE_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("월 누적은 다른 지갑의 충전이나 다른 종류의 거래를 합치지 않는다")
  void monthlyLimit_onlyThisWalletAndChargeType() {
    Wallet wallet = wallet(1, 0L, 0L);
    Wallet other = wallet(2, 0L, 0L);
    ledger(other, CHARGE, 1_000_000L, LocalDateTime.of(2026, 11, 5, 10, 0));
    ledger(wallet, CASHBACK_EARN, 500_000L, LocalDateTime.of(2026, 11, 6, 10, 0));

    assertThatCode(() -> check(wallet.getId(), 1_000_000L, NOW))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("한도를 낮추면 낮춘 값으로 검사하고, 잔액이 한도보다 커져도 기존 값은 그대로 두고 충전만 거절한다")
  void loweredLimits() {
    operatorSettingRepository.save(
        OperatorSetting.create(SettingKey.WALLET_HOLDING_LIMIT, 1_000_000L, null, NOW));
    operatorSettingRepository.save(
        OperatorSetting.create(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT, 700_000L, null, NOW));

    // 보유 한도를 100만원으로 낮춤: 잔액 60만원이면 40만원까지 충전 가능
    Wallet small = wallet(1, 600_000L, 0L);
    assertThatCode(() -> check(small.getId(), 400_000L, NOW))
        .doesNotThrowAnyException();
    assertFails(
        () -> check(small.getId(), 400_001L, NOW),
        WalletErrorCode.HOLDING_LIMIT_EXCEEDED);

    // 잔액이 이미 낮춘 한도보다 크면 충전만 거절하고 잔액은 그대로
    Wallet over = wallet(2, 1_500_000L, 0L);
    assertFails(
        () -> check(over.getId(), 1L, NOW),
        WalletErrorCode.HOLDING_LIMIT_EXCEEDED);
    assertThat(walletRepository.findById(over.getId()).orElseThrow().getBalance())
        .isEqualTo(1_500_000L);

    // 월 충전 한도를 70만원으로 낮춤: 이번 달 80만원을 이미 충전했다면 충전만 거절
    Wallet charged = wallet(3, 0L, 0L);
    ledger(charged, CHARGE, 800_000L, LocalDateTime.of(2026, 11, 5, 10, 0));
    assertFails(
        () -> check(charged.getId(), 1L, NOW),
        WalletErrorCode.MONTHLY_CHARGE_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("두 한도를 모두 넘으면 보유 한도(WAL-006)를 먼저 알린다")
  void bothExceeded_holdingFirst() {
    Wallet wallet = wallet(1, 1_900_000L, 0L);
    ledger(wallet, CHARGE, 1_000_000L, LocalDateTime.of(2026, 11, 5, 10, 0));

    assertFails(
        () -> check(wallet.getId(), 200_000L, NOW),
        WalletErrorCode.HOLDING_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("충전액이 매우 커도 넘침 없이 한도 초과로 거절한다")
  void hugeAmount_noOverflow() {
    Wallet wallet = wallet(1, 1_000L, 0L);

    assertFails(
        () -> check(wallet.getId(), Long.MAX_VALUE, NOW),
        WalletErrorCode.HOLDING_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("0 이하 금액으로 호출하면 거절한다")
  void nonPositiveAmount_rejected() {
    Wallet wallet = wallet(1, 0L, 0L);

    assertThatThrownBy(() -> check(wallet.getId(), 0L, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> check(wallet.getId(), -1L, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("잠그기 전에 읽어 둔 낡은 엔티티가 있어도 DB의 최신 잔액으로 검사한다")
  void staleEntity_doesNotAffectCheck() {
    Wallet wallet = wallet(1, 1_000_000L, 0L);

    assertFails(
        () ->
            new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                    status -> {
                      walletRepository.findById(wallet.getId()); // 먼저 일반 조회(낡은 객체가 남음)
                      // 그 사이 다른 트랜잭션이 90만원을 충전하고 커밋(실제 잔액 190만원)
                      inNewTransaction(
                          () -> walletService.credit(wallet.getId(), 900_000L, CHARGE, 1L));
                      chargeLimitService.assertChargeable(wallet.getId(), 200_000L, NOW);
                    }),
        WalletErrorCode.HOLDING_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("호출한 쪽의 트랜잭션 없이 부르면 바로 예외가 난다")
  void withoutTransaction_rejected() {
    Wallet wallet = wallet(1, 0L, 0L);

    assertThatThrownBy(() -> chargeLimitService.assertChargeable(wallet.getId(), 1L, NOW))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  @DisplayName("없는 지갑이면 WAL-001로 거절한다")
  void walletNotFound() {
    assertFails(() -> check(999_999L, 1L, NOW), WalletErrorCode.WALLET_NOT_FOUND);
  }

  // 데브머니·캐시백 잔액을 가진 지갑 저장. 회원 ID는 테스트마다 구분하기 위해 번호로 만듦
  private Wallet wallet(int number, long devMoney, long cashback) {
    Wallet wallet = Wallet.create(9_300L + number, NOW);
    if (devMoney > 0) {
      wallet.credit(devMoney, false, NOW);
    }
    if (cashback > 0) {
      wallet.credit(cashback, true, NOW);
    }
    return walletRepository.save(wallet);
  }

  // 월 누적 계산에 쓰는 원장 행. 한도 검사는 type, 금액, 시각, 지갑만 보므로 나머지는 단순하게 채움
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

  // 한도 검사는 호출한 쪽의 트랜잭션이 필수(MANDATORY)라서 트랜잭션 안에서 호출함. 실제로는 충전 서비스의 트랜잭션이 이 역할을 함
  private void check(Long walletId, long amount, LocalDateTime now) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> chargeLimitService.assertChargeable(walletId, amount, now));
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
