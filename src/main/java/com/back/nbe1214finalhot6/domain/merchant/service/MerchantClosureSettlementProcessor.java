package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlementStatus;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.MerchantWalletService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가맹점 한 곳의 해지 정산을 한 트랜잭션으로 처리한다. 호출하는 쪽은 트랜잭션을 열지 않는다.
 *
 * <p>월 정산에 묶이지 않은 달의 일 정산 확정액으로 월 정산 건을 만들고, 마지막 일 정산에 남은 부족분을 가장 최근 달부터 차감한 뒤 지갑을 거쳐 계좌로
 * 지급하고 해지 완료로 바꾼다. 부족분이 정산금보다 많으면 아무것도 바꾸지 않고 보류한다. 점주의 계좌와 이미 지급한 정산금은 건드리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MerchantClosureSettlementProcessor {

  public enum Outcome {
    /** 정산을 끝내고 해지 완료로 바꿨다 */
    CLOSED,
    /** 부족분을 갚을 수 없어 그대로 두었다 */
    HELD,
    /** 이미 다른 실행이 처리했다 */
    ALREADY
  }

  private record Payout(LocalDate month, long amount) {}

  private final MerchantRepository merchantRepository;
  private final DailySettlementRepository dailySettlementRepository;
  private final MonthlySettlementRepository monthlySettlementRepository;
  private final MerchantWalletService walletService;
  private final Clock clock;

  /**
   * @param closureDate 해지를 신청한 날. 이날까지의 일 정산이 끝나 있어야 한다
   */
  @Transactional
  public Outcome settleOne(Long merchantId, LocalDate closureDate) {
    long debt =
        dailySettlementRepository
            .findByMerchantIdAndSettlementDate(merchantId, closureDate)
            .map(DailySettlement::getCarryOut)
            .map(carryOut -> Math.max(0, -carryOut))
            .orElse(0L);

    List<Payout> payouts = unsettledPayouts(merchantId, closureDate);
    if (!offset(payouts, debt)) {
      return Outcome.HELD;
    }

    // 상태를 바꾸는 쪽을 하나로 만든다. 동시에 실행된 다른 쪽은 여기서 기다렸다가 0건이 되어 물러난다
    int updated =
        merchantRepository.updateStatusIfCurrent(
            merchantId, MerchantStatus.CLOSING, MerchantStatus.CLOSED);
    if (updated == 0) {
      return Outcome.ALREADY;
    }

    LocalDateTime now = LocalDateTime.now(clock);
    for (Payout payout : payouts) {
      if (payout.amount() > 0) {
        monthlySettlementRepository.save(
            MonthlySettlement.create(merchantId, payout.month(), payout.amount(), now));
      }
    }
    payPending(merchantId, now);
    return Outcome.CLOSED;
  }

  // 월 정산 건이 없는 달의 일 정산 확정액. 신청한 달은 신청일까지만 센다. 오래된 달부터 담는다
  private List<Payout> unsettledPayouts(Long merchantId, LocalDate closureDate) {
    LocalDate firstMonth =
        monthlySettlementRepository
            .findFirstByMerchantIdOrderBySettlementMonthDesc(merchantId)
            .map(monthly -> monthly.getSettlementMonth().plusMonths(1))
            .or(
                () ->
                    dailySettlementRepository
                        .findFirstByMerchantIdOrderBySettlementDateAsc(merchantId)
                        .map(daily -> daily.getSettlementDate().withDayOfMonth(1)))
            .orElse(closureDate.withDayOfMonth(1));

    YearMonth lastMonth = YearMonth.from(closureDate);
    List<Payout> payouts = new ArrayList<>();
    for (YearMonth month = YearMonth.from(firstMonth);
        !month.isAfter(lastMonth);
        month = month.plusMonths(1)) {
      LocalDate from = month.atDay(1);
      if (monthlySettlementRepository.existsByMerchantIdAndSettlementMonth(merchantId, from)) {
        continue;
      }
      LocalDate to = month.equals(lastMonth) ? closureDate.plusDays(1) : month.plusMonths(1).atDay(1);
      long sum =
          dailySettlementRepository.sumConfirmedAmountByMerchantBetween(merchantId, from, to);
      if (sum > 0) {
        payouts.add(new Payout(from, sum));
      }
    }
    return payouts;
  }

  // 부족분을 가장 최근 달의 지급액부터 깎는다. 다 갚으면 true, 정산금이 모자라면 false
  private boolean offset(List<Payout> payouts, long debt) {
    long remaining = debt;
    for (int i = payouts.size() - 1; i >= 0 && remaining > 0; i--) {
      Payout payout = payouts.get(i);
      long take = Math.min(payout.amount(), remaining);
      payouts.set(i, new Payout(payout.month(), payout.amount() - take));
      remaining -= take;
    }
    return remaining == 0;
  }

  // 지급이 끝나지 않은 건을 지갑에 입금하고 계좌로 출금한다. 같은 건을 다시 처리해도 중복 반영되지 않는다
  private void payPending(Long merchantId, LocalDateTime now) {
    for (MonthlySettlement pending :
        monthlySettlementRepository.findByMerchantIdAndStatusOrderByIdAsc(
            merchantId, MonthlySettlementStatus.PENDING)) {
      MonthlySettlement locked =
          monthlySettlementRepository
              .findWithLockById(pending.getId())
              .orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_REQUEST));
      if (locked.isPaid()) {
        continue;
      }
      walletService.deposit(merchantId, locked.getPayoutAmount(), locked.getId());
      walletService.withdrawToAccount(merchantId, locked.getPayoutAmount(), locked.getId());
      locked.markPaid(now);
    }
  }
}
