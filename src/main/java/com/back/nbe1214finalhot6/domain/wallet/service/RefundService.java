package com.back.nbe1214finalhot6.domain.wallet.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.entity.Refund;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.RefundRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불. 지갑(데브머니)에서 돈을 빼 모의계좌에 돌려줌. 환불할 수 있는 돈은 데브머니 잔액까지이며 캐시백·적립 예정은 환불할 수 없음. 탈퇴 정산(U8)의 남은
 * 데브머니 환불도 이 서비스를 씀.
 */
@Service
@RequiredArgsConstructor
public class RefundService {

  private final WalletRepository walletRepository;
  private final AccountRepository accountRepository;
  private final AccountTransactionRepository accountTransactionRepository;
  private final RefundRepository refundRepository;
  private final WalletService walletService;
  private final EntityManager entityManager;
  private final Clock clock;

  /**
   * 환불함. 아래를 한 트랜잭션으로 처리하고, 중간에 하나라도 실패하면 모두 롤백됨.
   *
   * <p>지갑 → 계좌 순서로 잠금(충전·환불·탈퇴 정산 공통). 지갑은 이 메서드에서 가장 먼저 읽고 바로 잠가야 함. 그 전에 지갑을 객체로 읽으면 낡은 잔액을
   * 볼 수 있음. 부분 환불을 허용하고, 환불해도 월 충전 한도는 복원하지 않음(충전 누적은 CHARGE만 합산).
   *
   * @param walletId 환불할 지갑
   * @param amount 환불액(양수)
   * @param idempotencyKey 멱등키. 같은 지갑의 같은 키는 한 번만 처리하고, 같은 금액이면 처음 결과를 돌려줌
   * @throws BusinessException 금액 0 이하(WAL-003), 지갑 없음(WAL-001), 탈퇴(WAL-002), 같은 키에 다른 금액(COM-003), 계좌 없음(WAL-008),
   *     데브머니 잔액 부족(WAL-004)
   */
  @Transactional
  public RefundResult refund(Long walletId, long amount, String idempotencyKey) {
    if (amount <= 0) {
      throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey는 필수");
    }

    // 1. 지갑 행 잠금. 같은 지갑의 환불·충전·결제·캐시백 지급이 이후부터 순서대로 처리됨
    Wallet wallet =
        walletRepository
            .findByIdForUpdate(walletId)
            .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
    if (wallet.isWithdrawn()) {
      throw new BusinessException(WalletErrorCode.WALLET_WITHDRAWN);
    }

    // 2. 같은 멱등키의 재요청이면 새로 처리하지 않고 처음 결과를 돌려줌(금액이 다르면 거절).
    //    잠금 안에서 확인하므로 동시 요청도 한 번만 반영됨
    Optional<Refund> done =
        refundRepository.findByWalletIdAndIdempotencyKey(walletId, idempotencyKey);
    if (done.isPresent()) {
      if (done.get().getAmount() != amount) {
        throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_MISMATCH);
      }
      return RefundResult.from(done.get());
    }

    // 3. 모의계좌 행 잠금(지갑을 잠근 뒤에 잠금)
    Account account =
        accountRepository
            .findByUserIdForUpdate(wallet.getUserId())
            .orElseThrow(() -> new BusinessException(WalletErrorCode.ACCOUNT_NOT_FOUND));
    // 같은 트랜잭션에서 잠그기 전에 이 계좌를 먼저 읽었다면 JPA가 그때 읽은 낡은 객체를 돌려줌.
    // 그대로 쓰면 그 사이 다른 요청이 커밋한 잔액 변경을 덮어쓰므로, 잠근 직후 DB의 최신 값으로 다시 읽음
    entityManager.refresh(account);

    // 4. 환불 기록 저장. 계좌·지갑 원장이 이 환불 ID를 ref_key로 씀
    LocalDateTime now = LocalDateTime.now(clock);
    Refund refund = refundRepository.save(Refund.create(walletId, amount, idempotencyKey, now));

    // 5. 지갑 차감과 지갑 원장 기록. 데브머니가 모자라면 WAL-004로 여기서 실패해 환불 기록까지 롤백됨.
    //    캐시백 잔액은 건드리지 않고, 환불 대상도 아님
    walletService.debit(walletId, amount, WalletTransactionType.REFUND, refund.getId(), false);

    // 6. 모의계좌 입금과 계좌 원장 기록(입금은 양수)
    long accountBalanceAfter = account.credit(amount);
    accountTransactionRepository.save(
        AccountTransaction.builder()
            .accountId(account.getId())
            .type(AccountTransactionType.REFUND)
            .amount(amount)
            .balanceAfter(accountBalanceAfter)
            .refKey(refund.getId())
            .createdAt(now)
            .build());

    return RefundResult.from(refund);
  }
}
