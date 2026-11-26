package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.dto.ClosurePreviewResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.ClosureResponse;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantClosure;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantClosureRepository;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlementStatus;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantAccount;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가맹점 해지 신청. 신청하면 곧바로 CLOSING 이 되어 새 결제와 QR 이 막히고, 철회는 없다.
 *
 * <p>정산금과 계좌 잔액으로 쌓인 부족분(취소·환수)을 갚을 수 없으면 신청을 받지 않는다. 해지 정산은 이후 단계에서 처리한다.
 */
@Service
@RequiredArgsConstructor
public class MerchantClosureService {

  // 월 정산이 하나도 없는 가맹점의 일 정산을 처음부터 읽기 위한 기준일
  private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

  private final MerchantRepository merchantRepository;
  private final MerchantClosureRepository closureRepository;
  private final DailySettlementRepository dailySettlementRepository;
  private final MonthlySettlementRepository monthlySettlementRepository;
  private final MerchantAccountRepository accountRepository;
  private final Clock clock;

  /** 해지 신청 전에 정산 현황과 신청 가능 여부를 보여 준다. 상태는 바꾸지 않는다. */
  @Transactional(readOnly = true)
  public ClosurePreviewResponse preview(Long merchantId) {
    Merchant merchant = find(merchantId);
    return previewOf(merchant);
  }

  /**
   * 해지를 신청한다.
   *
   * @throws BusinessException 이미 해지 진행 중이면 MER-003, 해지됐으면 MER-004, 부족분을 갚을 수 없으면 MER-005
   */
  @Transactional
  public ClosureResponse request(Long merchantId) {
    Merchant merchant = find(merchantId);
    MerchantStatusGuard.assertActive(merchant.getStatus());

    if (!previewOf(merchant).closable()) {
      throw new BusinessException(MerchantErrorCode.MERCHANT_CLOSURE_SHORTFALL);
    }

    // 동시에 두 번 눌러도 상태를 ACTIVE 에서 바꾸는 쪽은 하나뿐이다
    int updated =
        merchantRepository.updateStatusIfCurrent(
            merchantId, MerchantStatus.ACTIVE, MerchantStatus.CLOSING);
    if (updated == 0) {
      MerchantStatus current =
          merchantRepository
              .findStatusById(merchantId)
              .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
      MerchantStatusGuard.assertActive(current);
      throw new BusinessException(MerchantErrorCode.MERCHANT_CLOSING);
    }

    MerchantClosure closure =
        closureRepository.save(MerchantClosure.create(merchantId, LocalDateTime.now(clock)));
    return new ClosureResponse(MerchantStatus.CLOSING, closure.getRequestedAt());
  }

  private Merchant find(Long merchantId) {
    return merchantRepository
        .findById(merchantId)
        .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
  }

  private ClosurePreviewResponse previewOf(Merchant merchant) {
    Long merchantId = merchant.getId();

    long carryDebt =
        dailySettlementRepository
            .findFirstByMerchantIdOrderBySettlementDateDesc(merchantId)
            .map(DailySettlement::getCarryOut)
            .map(carryOut -> Math.max(0, -carryOut))
            .orElse(0L);

    // 지급 대기 월 정산 + 마지막 월 정산 이후의 일 정산 확정액이 아직 계좌로 나가지 않은 정산금이다
    LocalDate unsettledFrom =
        monthlySettlementRepository
            .findFirstByMerchantIdOrderBySettlementMonthDesc(merchantId)
            .map(monthly -> monthly.getSettlementMonth().plusMonths(1))
            .orElse(EARLIEST);
    long unpaidAmount =
        monthlySettlementRepository.sumPayoutAmountByStatus(
                merchantId, MonthlySettlementStatus.PENDING)
            + dailySettlementRepository.sumConfirmedAmountFrom(merchantId, unsettledFrom);

    long accountBalance =
        accountRepository.findByMerchantId(merchantId).map(MerchantAccount::getBalance).orElse(0L);

    long shortfall = Math.max(0, carryDebt - unpaidAmount - accountBalance);
    return new ClosurePreviewResponse(
        merchant.getStatus(),
        unpaidAmount,
        carryDebt,
        accountBalance,
        shortfall == 0,
        shortfall);
  }
}
