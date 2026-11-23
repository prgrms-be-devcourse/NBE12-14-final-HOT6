package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * 적립 한 건의 지급 처리. 건별 트랜잭션의 경계이며, 지급 배치({@link CashbackPayoutService})가 건마다 호출한다. 트랜잭션이 걸리도록 배치와 다른 빈에
 * 둔다.
 */
@Service
@RequiredArgsConstructor
public class CashbackPayoutProcessor {

  private final CashbackEarningRepository earningRepository;
  private final WalletService walletService;
  private final CashbackBudgetService budgetService;
  private final Clock clock;

  /**
   * 적립 한 건을 지갑에 입금하고 지급 완료로 바꾼다. 새 트랜잭션에서 실행하며, 입금과 상태 전환, 예산 이동이 한 번에 커밋되거나 한 번에 롤백된다.
   *
   * <p>순서는 지갑 입금, 상태 전환, 예산 이동이다. 결제 취소가 지갑, 적립 건, 예산 순으로 잠그므로 같은 순서를 따라야 교착하지 않는다. 입금은
   * {@code (CASHBACK_EARN, 적립 건 ID)} 로 한 번만 반영되고, 상태 전환은 {@code where status = PENDING} 조건부 UPDATE 라서 같은 건을
   * 두 배치가 처리해도 한쪽만 지급한다.
   *
   * <p>입금한 뒤 상태 전환이 0건이면(그 사이 취소됐거나 다른 배치가 먼저 지급했다) 이 트랜잭션을 롤백 전용으로 표시해 입금을 되돌리고
   * {@link PayoutOutcome#SKIPPED} 를 돌려준다.
   *
   * @throws com.back.nbe1214finalhot6.common.exception.BusinessException 지갑이 없거나 탈퇴했을 때(WAL-001·002), 예약한 예산보다
   *     많이 옮기려 할 때(CSH-007). 예외가 나면 입금도 함께 롤백되어 적립 예정으로 남는다
   */
  @Transactional
  public PayoutOutcome pay(Long earningId) {
    LocalDateTime now = LocalDateTime.now(clock);
    CashbackEarning earning = earningRepository.findById(earningId).orElse(null);
    if (earning == null
        || earning.getStatus() != EarningStatus.PENDING
        || earning.getPayableAt() == null
        || earning.getPayableAt().isAfter(now)) {
      return PayoutOutcome.SKIPPED;
    }

    walletService.credit(
        earning.getWalletId(), earning.getAmount(), WalletTransactionType.CASHBACK_EARN, earningId);
    if (earningRepository.markPaidIfPending(
            earningId, now, EarningStatus.PENDING, EarningStatus.PAID)
        == 0) {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      return PayoutOutcome.SKIPPED;
    }
    YearMonth month = YearMonth.from(earning.getLimitMonth());
    budgetService.settleBudget(month.getYear(), month.getMonthValue(), earning.getAmount());
    return PayoutOutcome.PAID;
  }
}
