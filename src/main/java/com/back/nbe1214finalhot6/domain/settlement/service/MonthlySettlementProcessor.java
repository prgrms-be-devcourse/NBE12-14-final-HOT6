package com.back.nbe1214finalhot6.domain.settlement.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.service.SettlementPayoutService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 월 정산 건 생성과 지급을 각각 한 트랜잭션으로 처리한다. 호출하는 쪽은 트랜잭션을 열지 않는다(해지 정산은 자기 트랜잭션에 참여시킨다). */
@Service
@RequiredArgsConstructor
public class MonthlySettlementProcessor {

  private final MonthlySettlementRepository settlementRepository;
  private final MerchantRepository merchantRepository;
  private final SettlementPayoutService payoutService;
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

  /**
   * 점주의 모의계좌에 지급하고 지급 완료로 바꾼다. 둘 중 하나라도 실패하면 함께 되돌아가 건은 대기로 남고, 다음 실행에서 다시 지급한다. 락 순서는 정산 건 → 계좌.
   *
   * @throws BusinessException 가맹점이 없으면 MER-001, 점주 계좌가 없으면 WAL-008
   */
  @Transactional
  public void payAndComplete(Long settlementId) {
    MonthlySettlement settlement =
        settlementRepository
            .findWithLockById(settlementId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_REQUEST));
    if (settlement.isPaid()) {
      return;
    }
    Long ownerId =
        merchantRepository
            .findById(settlement.getMerchantId())
            .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND))
            .getOwnerId();
    if (ownerId == null) {
      // 점주가 없는 가맹점(공공데이터 적재분)은 받을 계좌가 없다
      throw new BusinessException(WalletErrorCode.ACCOUNT_NOT_FOUND);
    }
    payoutService.payout(ownerId, settlement.getPayoutAmount(), settlement.getId());
    settlement.markPaid(LocalDateTime.now(clock));
  }
}
