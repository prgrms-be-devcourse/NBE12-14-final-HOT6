package com.back.nbe1214finalhot6.domain.wallet.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.entity.Charge;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.ChargeRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 충전. 모의계좌에서 돈을 빼 지갑(데브머니)에 넣음. 토스 결제창 연동(W6) 전의 결제창 없는 흐름. */
@Service
@RequiredArgsConstructor
public class ChargeService {

  private final WalletRepository walletRepository;
  private final AccountRepository accountRepository;
  private final AccountTransactionRepository accountTransactionRepository;
  private final ChargeRepository chargeRepository;
  private final ChargeLimitService chargeLimitService;
  private final WalletService walletService;
  private final EntityManager entityManager;
  private final Clock clock;

  /**
   * 충전함. 아래를 한 트랜잭션으로 처리하고, 중간에 하나라도 실패하면 모두 롤백됨.
   *
   * <p>지갑 → 계좌 순서로 잠금(충전·환불·탈퇴 정산 공통). 지갑은 이 메서드에서 가장 먼저 읽고 바로 잠가야 함. 그 전에 지갑을 객체로 읽으면 낡은 잔액을
   * 볼 수 있음.
   *
   * @param walletId 충전받을 지갑
   * @param amount 충전액(양수)
   * @param idempotencyKey 멱등키. 같은 지갑의 같은 키는 한 번만 처리하고, 같은 금액이면 처음 결과를 돌려줌
   * @throws BusinessException 금액 0 이하(WAL-003), 지갑 없음(WAL-001), 탈퇴(WAL-002), 같은 키에 다른 금액(COM-003), 계좌 없음(WAL-008),
   *     계좌 잔액 부족(WAL-009), 보유 한도 초과(WAL-006), 월 충전 한도 초과(WAL-007)
   */
  @Transactional
  public ChargeResult charge(Long walletId, long amount, String idempotencyKey) {
    if (amount <= 0) {
      throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey는 필수");
    }

    // 1. 지갑 행 잠금. 같은 지갑의 충전·결제·캐시백 지급이 이후부터 순서대로 처리됨
    Wallet wallet =
        walletRepository
            .findByIdForUpdate(walletId)
            .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
    if (wallet.isWithdrawn()) {
      throw new BusinessException(WalletErrorCode.WALLET_WITHDRAWN);
    }

    // 2. 같은 멱등키의 재요청이면 새로 처리하지 않고 처음 결과를 돌려줌(금액이 다르면 거절).
    //    잠금 안에서 확인하므로 동시 요청도 한 번만 반영됨
    Optional<Charge> done =
        chargeRepository.findByWalletIdAndIdempotencyKey(walletId, idempotencyKey);
    if (done.isPresent()) {
      if (done.get().getAmount() != amount) {
        throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_MISMATCH);
      }
      return ChargeResult.from(done.get());
    }

    // 3. 모의계좌 행 잠금과 잔액 확인(지갑을 잠근 뒤에 잠금)
    Account account =
        accountRepository
            .findByUserIdForUpdate(wallet.getUserId())
            .orElseThrow(() -> new BusinessException(WalletErrorCode.ACCOUNT_NOT_FOUND));
    // 같은 트랜잭션에서 잠그기 전에 이 계좌를 먼저 읽었다면 JPA가 그때 읽은 낡은 객체를 돌려줌.
    // 그대로 쓰면 그 사이 다른 요청이 커밋한 잔액 변경을 덮어쓰므로, 잠근 직후 DB의 최신 값으로 다시 읽음
    entityManager.refresh(account);
    if (account.getBalance() < amount) {
      throw new BusinessException(WalletErrorCode.INSUFFICIENT_ACCOUNT_BALANCE);
    }

    // 4. 한도 검사(보유 한도, 월 충전 한도)
    LocalDateTime now = LocalDateTime.now(clock);
    chargeLimitService.assertChargeable(walletId, amount, now);

    // 5. 충전 기록 저장. 계좌·지갑 원장이 이 충전 ID를 ref_key로 씀
    Charge charge = chargeRepository.save(Charge.create(walletId, amount, idempotencyKey, now));

    // 6. 모의계좌 차감과 계좌 원장 기록(차감은 음수)
    long accountBalanceAfter = account.debit(amount);
    accountTransactionRepository.save(
        AccountTransaction.builder()
            .accountId(account.getId())
            .type(AccountTransactionType.CHARGE)
            .amount(-amount)
            .balanceAfter(accountBalanceAfter)
            .refKey(charge.getId())
            .createdAt(now)
            .build());

    // 7. 지갑 입금과 지갑 원장 기록
    walletService.credit(walletId, amount, WalletTransactionType.CHARGE, charge.getId());

    return ChargeResult.from(charge);
  }
}
