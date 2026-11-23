package com.back.nbe1214finalhot6.domain.settlement.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.MerchantWalletService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 월 정산 지급 중 DB 를 함께 바꿔야 하는 단계를 한 트랜잭션으로 처리한다. 호출하는 쪽은 트랜잭션을 열지 않는다. */
@Service
@RequiredArgsConstructor
public class MonthlySettlementProcessor {

  private final MonthlySettlementRepository settlementRepository;
  private final MerchantWalletService walletService;
  private final Clock clock;

  /** 새로 만들었으면 true, 이미 있는 건이어서 넘겼으면 false 를 돌려준다. */
  @Transactional
  public boolean createIfAbsent(Long merchantId, LocalDate settlementMonth, long payoutAmount) {
    if (settlementRepository.existsByMerchantIdAndSettlementMonth(merchantId, settlementMonth)) {
      return false;
    }
    settlementRepository.save(
        MonthlySettlement.create(
            merchantId, settlementMonth, payoutAmount, LocalDateTime.now(clock)));
    return true;
  }

  /** 지갑에서 계좌로 출금하고 지급 완료로 바꾼다. 둘 중 하나라도 실패하면 함께 되돌린다. */
  @Transactional
  public void withdrawAndComplete(Long settlementId) {
    MonthlySettlement settlement =
        settlementRepository
            .findById(settlementId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_REQUEST));
    if (settlement.isPaid()) {
      return;
    }
    walletService.withdrawToAccount(
        settlement.getMerchantId(), settlement.getPayoutAmount(), settlement.getId());
    settlement.markPaid(LocalDateTime.now(clock));
  }
}
