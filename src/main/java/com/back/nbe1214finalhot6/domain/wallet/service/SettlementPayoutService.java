package com.back.nbe1214finalhot6.domain.wallet.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가맹점 월 정산금을 점주의 모의계좌에 입금한다. 점주의 모의계좌가 곧 정산 계좌다. 호출한 쪽의 트랜잭션에 참여한다.
 *
 * <p>계좌 원장에 {@link AccountTransactionType#SETTLEMENT_PAYOUT} 으로 남기고 ref_key 는 월 정산 건 ID 다. 같은 건으로 다시
 * 호출하면 반영하지 않는다(원장의 (type, ref_key) 유니크와 계좌 잠금으로 보장).
 */
@Service
@RequiredArgsConstructor
public class SettlementPayoutService {

  private final AccountRepository accountRepository;
  private final AccountTransactionRepository transactionRepository;
  private final EntityManager entityManager;
  private final Clock clock;

  /**
   * @param ownerId 가맹점 점주의 회원 ID
   * @param settlementId 월 정산 건 ID
   * @throws BusinessException 금액이 0 이하(WAL-003), 점주 계좌가 없을 때(WAL-008)
   */
  @Transactional
  public void payout(Long ownerId, long amount, Long settlementId) {
    if (amount <= 0) {
      throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
    }
    // 같은 건을 동시에 지급해도 원장 확인과 입금이 겹치지 않도록 계좌를 먼저 잠근다
    Account account =
        accountRepository
            .findByUserIdForUpdate(ownerId)
            .orElseThrow(() -> new BusinessException(WalletErrorCode.ACCOUNT_NOT_FOUND));
    // 같은 트랜잭션에서 잠그기 전에 이 계좌를 먼저 읽었다면 JPA 가 그때 읽은 낡은 객체를 돌려준다(RefundService 와 같은 이유).
    // 그대로 쓰면 그 사이 다른 요청이 커밋한 잔액 변경을 덮어쓰므로, 잠근 직후 DB 의 최신 값으로 다시 읽는다.
    // 잠금 조회 전에 아직 반영하지 않은 변경은 자동으로 DB 에 반영되므로, 한 트랜잭션에서 여러 건을 지급해도 앞선 입금이 사라지지 않는다
    entityManager.refresh(account);

    var applied =
        transactionRepository.findByTypeAndRefKey(
            AccountTransactionType.SETTLEMENT_PAYOUT, settlementId);
    if (applied.isPresent()) {
      // 같은 건인데 금액이 다르면 정산 쪽 오류이므로 조용히 넘기지 않는다
      if (applied.get().getAmount() != amount) {
        throw new IllegalStateException("같은 월 정산 건의 지급액이 다릅니다 settlementId=" + settlementId);
      }
      return;
    }

    long balanceAfter = account.credit(amount);
    transactionRepository.save(
        AccountTransaction.builder()
            .accountId(account.getId())
            .type(AccountTransactionType.SETTLEMENT_PAYOUT)
            .amount(amount)
            .balanceAfter(balanceAfter)
            .refKey(settlementId)
            .createdAt(LocalDateTime.now(clock))
            .build());
  }
}
