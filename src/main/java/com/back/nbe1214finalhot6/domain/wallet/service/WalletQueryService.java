package com.back.nbe1214finalhot6.domain.wallet.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.wallet.dto.WalletResponse;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletStatus;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletSummary;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지갑 조회. 지갑을 잠그지 않는 읽기 전용이며, 적립 예정은 B의 적립 내역 API와 프런트가 합쳐서 보여 줌. */
@Service
@RequiredArgsConstructor
public class WalletQueryService {

  private final WalletRepository walletRepository;
  private final ChargeLimitService chargeLimitService;
  private final Clock clock;

  /**
   * 내 지갑의 잔액과 충전 한도 현황을 돌려줌.
   *
   * <p>지갑은 엔티티가 아니라 값으로 읽어서, 같은 요청에서 먼저 읽어 둔 낡은 객체에 영향받지 않음. 한도와 이번 달 충전 누적은 충전 검사(ChargeLimitService)와
   * 같은 규칙으로 계산함.
   *
   * @throws BusinessException 지갑 없음(WAL-001), 탈퇴한 지갑(WAL-002)
   */
  @Transactional(readOnly = true)
  public WalletResponse getMyWallet(Long userId) {
    WalletSummary wallet =
        walletRepository
            .findSummaryByUserId(userId)
            .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
    if (wallet.getStatus() == WalletStatus.WITHDRAWN) {
      throw new BusinessException(WalletErrorCode.WALLET_WITHDRAWN);
    }

    LocalDateTime now = LocalDateTime.now(clock);
    long holdingLimit = chargeLimitService.holdingLimit();
    long monthlyChargeLimit = chargeLimitService.monthlyChargeLimit();
    long chargedThisMonth = chargeLimitService.chargedThisMonth(wallet.getId(), now);

    return new WalletResponse(
        wallet.getBalance(),
        wallet.getCashbackBalance(),
        // 캐시백 잔액이 음수(회수로 생긴 부채)면 쓸 수 있는 캐시백은 0
        Math.max(0, wallet.getCashbackBalance()),
        holdingLimit,
        monthlyChargeLimit,
        chargedThisMonth,
        ChargeLimitService.chargeableAmount(
            wallet.getBalance(), holdingLimit, monthlyChargeLimit, chargedThisMonth));
  }
}
