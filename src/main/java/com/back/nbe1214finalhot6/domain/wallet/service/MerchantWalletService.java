package com.back.nbe1214finalhot6.domain.wallet.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantAccount;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.MerchantWalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletTransactionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가맹점 지갑·계좌 관리. 모든 메서드는 호출한 쪽의 트랜잭션에 참여함. */
@Service
@RequiredArgsConstructor
public class MerchantWalletService {

  private final MerchantWalletRepository walletRepository;
  private final MerchantAccountRepository accountRepository;
  private final MerchantWalletTransactionRepository transactionRepository;
  private final Clock clock;

  /** 가맹점 등록 때 잔액 0원의 지갑과 계좌를 함께 만듦. */
  @Transactional
  public void createFor(Long merchantId) {
    LocalDateTime now = LocalDateTime.now(clock);
    walletRepository.save(MerchantWallet.create(merchantId, now));
    accountRepository.save(MerchantAccount.create(merchantId, now));
  }

  /**
   * 월 정산 지급금을 지갑에 입금함. 같은 refKey로 다시 호출하면 반영하지 않음.
   *
   * @throws BusinessException 금액이 0 이하(MWL-002), 지갑이 없을 때(MWL-001), 같은 refKey의 금액이 다를 때(MWL-005)
   */
  @Transactional
  public void deposit(Long merchantId, long amount, Long refKey) {
    validateAmount(amount);
    MerchantWallet wallet = lockWallet(merchantId);

    var applied =
        transactionRepository.findByTypeAndRefKey(
            MerchantWalletTransactionType.SETTLEMENT_PAYOUT, refKey);
    if (applied.isPresent()) {
      verifySameAmount(applied.get(), amount);
      return;
    }

    LocalDateTime now = LocalDateTime.now(clock);
    wallet.deposit(amount, now);
    record(wallet, MerchantWalletTransactionType.SETTLEMENT_PAYOUT, amount, refKey, now);
  }

  /**
   * 지갑에서 가맹점 계좌로 출금함. 지갑 차감, 계좌 증가, 원장 기록이 한 트랜잭션이며 같은 refKey는 중복 반영하지 않음.
   *
   * @throws BusinessException 금액이 0 이하(MWL-002), 지갑 없음(MWL-001), 잔액 부족(MWL-003), 계좌 없음(MWL-004)
   */
  @Transactional
  public void withdrawToAccount(Long merchantId, long amount, Long refKey) {
    validateAmount(amount);
    MerchantWallet wallet = lockWallet(merchantId);

    var applied =
        transactionRepository.findByTypeAndRefKey(
            MerchantWalletTransactionType.PAYOUT_TO_ACCOUNT, refKey);
    if (applied.isPresent()) {
      verifySameAmount(applied.get(), -amount);
      return;
    }

    if (wallet.getBalance() < amount) {
      throw new BusinessException(MerchantWalletErrorCode.INSUFFICIENT_BALANCE);
    }
    MerchantAccount account =
        accountRepository
            .findByMerchantId(merchantId)
            .orElseThrow(
                () -> new BusinessException(MerchantWalletErrorCode.MERCHANT_ACCOUNT_NOT_FOUND));

    LocalDateTime now = LocalDateTime.now(clock);
    wallet.withdraw(amount, now);
    account.deposit(amount);
    record(wallet, MerchantWalletTransactionType.PAYOUT_TO_ACCOUNT, -amount, refKey, now);
  }

  // 같은 refKey로 금액이 다르게 들어오면 정산 쪽 오류이므로 조용히 넘기지 않고 거절함
  private void verifySameAmount(MerchantWalletTransaction applied, long requestedSignedAmount) {
    if (applied.getAmount() != requestedSignedAmount) {
      throw new BusinessException(MerchantWalletErrorCode.AMOUNT_MISMATCH);
    }
  }

  private void validateAmount(long amount) {
    if (amount <= 0) {
      throw new BusinessException(MerchantWalletErrorCode.INVALID_AMOUNT);
    }
  }

  private MerchantWallet lockWallet(Long merchantId) {
    return walletRepository
        .findWithLockByMerchantId(merchantId)
        .orElseThrow(
            () -> new BusinessException(MerchantWalletErrorCode.MERCHANT_WALLET_NOT_FOUND));
  }

  private void record(
      MerchantWallet wallet,
      MerchantWalletTransactionType type,
      long signedAmount,
      Long refKey,
      LocalDateTime now) {
    transactionRepository.save(
        MerchantWalletTransaction.builder()
            .walletId(wallet.getId())
            .type(type)
            .amount(signedAmount)
            .balanceAfter(wallet.getBalance())
            .refKey(refKey)
            .createdAt(now)
            .build());
  }
}
