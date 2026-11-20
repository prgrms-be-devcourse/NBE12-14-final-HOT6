package com.back.nbe1214finalhot6.domain.wallet.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletTransactionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지갑 입금·차감. 호출한 쪽(A 결제, B 캐시백)의 트랜잭션에 참여함. */
@Service
@RequiredArgsConstructor
public class WalletService {

  private final WalletRepository walletRepository;
  private final WalletTransactionRepository walletTransactionRepository;
  private final Clock clock;

  /**
   * 원장(WalletTransaction)에 한 줄로 남길 거래 한 건. WalletService 안에서만 쓰는 임시 묶음.
   *
   * <p>apply가 거래를 여러 건 받을 수 있도록 "금액 + 거래 종류" 한 쌍을 묶은 것. 예를 들어 충전금 2만원 +
   * 캐시백 1만원 결제는 LedgerLine 2개(2만원/PAYMENT_DEVMONEY, 1만원/PAYMENT_CASHBACK)가 되고, 각각 원장 한 줄이
   * 됨. amount는 항상 양수이며 입금(+)·차감(-) 부호는 원장에 기록할 때 붙임.
   */
  private record LedgerLine(long amount, WalletTransactionType type) {}

  // 트랜잭션을 새로 열지 않고 호출한 쪽에 참여(REQUIRED). 예외가 나면 호출한 쪽도 함께 롤백됨
  @Transactional
  public void credit(Long walletId, long amount, WalletTransactionType type, Long refKey) {
    apply(walletId, refKey, true, false, new LedgerLine(amount, type));
  }

  @Transactional
  public void debit(
      Long walletId, long amount, WalletTransactionType type, Long refKey, boolean allowNegative) {
    apply(walletId, refKey, false, allowNegative, new LedgerLine(amount, type));
  }

  /**
   * 결제 차감(A). 충전금 사용분과 캐시백 사용분을 한 번에 반영함.
   * 한 쪽이 부족하면 예외가 나고 다른 쪽 차감도 함께 롤백됨. 0원인 쪽은 건너뜀. refKey는 결제 ID.
   */
  @Transactional
  public void debitPayment(
      Long walletId, long devMoneyAmount, long cashbackAmount, Long paymentId) {
    Objects.requireNonNull(paymentId, "paymentId");
    apply(
        walletId,
        paymentId,
        false,
        false,
        paymentLines(
            devMoneyAmount,
            cashbackAmount,
            WalletTransactionType.PAYMENT_DEVMONEY,
            WalletTransactionType.PAYMENT_CASHBACK));
  }

  /**
   * 결제 취소 환급(A). 결제 때 낸 칸 그대로 돌려줌. 같은 결제 ID로 다시 불러도 한 번만 반영됨.
   *
   * <p>같은 지갑·같은 결제 ID로 차감한 기록이 없거나 환급액이 그때 낸 금액보다 크면 IllegalStateException. 지갑이 돈을 만들어내는 일을
   * 막는 내부 방어이며, 정상 흐름에서는 발생하지 않는 호출 버그로 봄.
   */
  @Transactional
  public void creditPaymentCancel(
      Long walletId, long devMoneyAmount, long cashbackAmount, Long paymentId) {
    Objects.requireNonNull(paymentId, "paymentId");
    LedgerLine[] lines =
        paymentLines(
            devMoneyAmount,
            cashbackAmount,
            WalletTransactionType.PAYMENT_CANCEL_DEVMONEY,
            WalletTransactionType.PAYMENT_CANCEL_CASHBACK);
    for (LedgerLine line : lines) {
      verifyRefundable(walletId, paymentId, line);
    }
    apply(walletId, paymentId, true, false, lines);
  }

  // 환급할 칸의 원래 결제 차감 기록을 찾아 지갑이 같고 환급액이 낸 금액 이하인지 확인함
  private void verifyRefundable(Long walletId, Long paymentId, LedgerLine cancelLine) {
    WalletTransactionType paidType =
        cancelLine.type() == WalletTransactionType.PAYMENT_CANCEL_DEVMONEY
            ? WalletTransactionType.PAYMENT_DEVMONEY
            : WalletTransactionType.PAYMENT_CASHBACK;
    WalletTransaction paid =
        walletTransactionRepository
            .findByTypeAndRefKey(paidType, paymentId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "환급할 결제 차감 기록이 없음: " + paidType + ", paymentId=" + paymentId));
    if (!paid.getWalletId().equals(walletId)) {
      throw new IllegalStateException(
          "결제를 차감한 지갑과 환급할 지갑이 다름: paymentId=" + paymentId);
    }
    // 차감 거래의 amount는 음수이므로 부호를 뒤집어 낸 금액과 비교
    if (cancelLine.amount() > -paid.getAmount()) {
      throw new IllegalStateException(
          "환급액이 결제 때 낸 금액보다 큼: " + cancelLine.amount() + " > " + (-paid.getAmount()));
    }
  }

  /**
   * 결제(차감)·결제 취소(환급)에서 충전금·캐시백 사용분을 LedgerLine 목록으로 바꿈. 결제와 취소가 같은 규칙을 쓰도록 공통으로 둔 메서드.
   * 차감인지 환급인지는 넘겨받은 type(PAYMENT_* 또는 PAYMENT_CANCEL_*)이 정함.
   *
   * <p>금액이 음수이거나 둘 다 0원이면 WAL-003으로 거절함. 한쪽만 0원이면 그쪽은 LedgerLine을 만들지 않음(원장에 0원 거래를 남기지
   * 않기 위함). 예: 충전금 3만원 + 캐시백 0원 → 충전금 LedgerLine 1개.
   */
  private static LedgerLine[] paymentLines(
      long devMoneyAmount,
      long cashbackAmount,
      WalletTransactionType devMoneyType,
      WalletTransactionType cashbackType) {
    if (devMoneyAmount < 0 || cashbackAmount < 0 || (devMoneyAmount == 0 && cashbackAmount == 0)) {
      throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
    }
    List<LedgerLine> lines = new ArrayList<>();
    if (devMoneyAmount > 0) {
      lines.add(new LedgerLine(devMoneyAmount, devMoneyType));
    }
    if (cashbackAmount > 0) {
      lines.add(new LedgerLine(cashbackAmount, cashbackType));
    }
    return lines.toArray(LedgerLine[]::new);
  }

  // 입금·차감의 공통 처리. 지갑을 한 번 잠그고 lines의 거래들을 한 트랜잭션 안에서 모두 반영함.
  // credit이 true면 입금, false면 차감. refKey는 중복 방지 키(결제 ID, 적립 건 ID 등)
  private void apply(
      Long walletId, Long refKey, boolean credit, boolean allowNegative, LedgerLine... lines) {
    for (LedgerLine line : lines) {
      // 방향이 다른 type은 호출 실수(버그)이므로 내부 방어로 막음
      if (line.type().isCredit() != credit) {
        throw new IllegalArgumentException(
            line.type() + "은(는) " + (line.type().isCredit() ? "입금" : "차감") + " 거래입니다");
      }
      if (line.amount() <= 0) {
        throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
      }
    }

    // 1. 지갑 행 잠금. 같은 지갑의 요청은 이후부터 순서대로 처리됨
    Wallet wallet =
        walletRepository
            .findByIdForUpdate(walletId)
            .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

    // 2. 이미 반영된 (type, refKey)는 건너뜀. 잠금 안에서 확인하므로 동시 요청도 한 번만 반영됨
    //    (유니크 위반 예외에 기대지 않음). 남은 게 없으면 정상 반환
    List<LedgerLine> pending =
        Arrays.stream(lines)
            .filter(
                line ->
                    refKey == null
                        || !walletTransactionRepository.existsByTypeAndRefKey(line.type(), refKey))
            .toList();
    if (pending.isEmpty()) {
      return;
    }

    // 3. 탈퇴한 지갑은 거절
    if (wallet.isWithdrawn()) {
      throw new BusinessException(WalletErrorCode.WALLET_WITHDRAWN);
    }

    // 4. 잔액 반영과 원장 기록(입금 +, 차감 -)
    LocalDateTime now = LocalDateTime.now(clock);
    for (LedgerLine line : pending) {
      boolean cashback = line.type().affectsCashback();
      long balanceAfter =
          credit
              ? wallet.credit(line.amount(), cashback, now)
              : wallet.debit(line.amount(), cashback, allowNegative, now);

      walletTransactionRepository.save(
          WalletTransaction.builder()
              .walletId(walletId)
              .type(line.type())
              .amount(credit ? line.amount() : -line.amount())
              .balanceAfter(balanceAfter)
              .refKey(refKey)
              .createdAt(now)
              .build());
    }
  }
}
