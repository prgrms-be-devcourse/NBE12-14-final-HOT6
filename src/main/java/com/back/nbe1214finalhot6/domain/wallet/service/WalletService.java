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

  // 트랜잭션을 새로 열지 않고 호출한 쪽에 참여(REQUIRED). 예외가 나면 호출한 쪽도 함께 롤백됨
  @Transactional
  public void credit(Long walletId, long amount, WalletTransactionType type, Long refKey) {
    apply(walletId, amount, type, refKey, true, false);
  }

  @Transactional
  public void debit(
      Long walletId, long amount, WalletTransactionType type, Long refKey, boolean allowNegative) {
    apply(walletId, amount, type, refKey, false, allowNegative);
  }

  private void apply(
      Long walletId,
      long amount,
      WalletTransactionType type,
      Long refKey,
      boolean credit,
      boolean allowNegative) {
    // 방향이 다른 type은 호출 실수(버그)이므로 내부 방어로 막음
    if (type.isCredit() != credit) {
      throw new IllegalArgumentException(
          type + "은(는) " + (type.isCredit() ? "입금" : "차감") + " 거래입니다");
    }
    if (amount <= 0) {
      throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
    }

    // 1. 지갑 행 잠금. 같은 지갑의 요청은 이후부터 순서대로 처리됨
    Wallet wallet =
        walletRepository
            .findByIdForUpdate(walletId)
            .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

    // 2. 같은 (type, refKey) 재요청이면 반영하지 않고 정상 반환.
    //    잠금 안에서 확인하므로 동시 요청도 한 번만 반영됨(유니크 위반 예외에 기대지 않음)
    if (refKey != null && walletTransactionRepository.existsByTypeAndRefKey(type, refKey)) {
      return;
    }

    // 3. 탈퇴한 지갑은 거절
    if (wallet.isWithdrawn()) {
      throw new BusinessException(WalletErrorCode.WALLET_WITHDRAWN);
    }

    // 4. 잔액 반영과 원장 기록(입금 +, 차감 -)
    LocalDateTime now = LocalDateTime.now(clock);
    boolean cashback = type.affectsCashback();
    long balanceAfter =
        credit
            ? wallet.credit(amount, cashback, now)
            : wallet.debit(amount, cashback, allowNegative, now);

    walletTransactionRepository.save(
        WalletTransaction.builder()
            .walletId(walletId)
            .type(type)
            .amount(credit ? amount : -amount)
            .balanceAfter(balanceAfter)
            .refKey(refKey)
            .createdAt(now)
            .build());
  }
}
