package com.back.nbe1214finalhot6.domain.wallet.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletTransactionRepository;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.service.OperatorSettingService;
import java.time.LocalDateTime;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 충전 한도 검사. 보유 한도와 월 충전 한도를 넘는 충전을 거절함. 한도 값은 운영자 설정에서 읽음. */
@Service
@RequiredArgsConstructor
public class ChargeLimitService {

  private final OperatorSettingService operatorSettingService;
  private final WalletTransactionRepository walletTransactionRepository;

  /**
   * 충전해도 한도를 넘지 않는지 검사함. 한도에 딱 맞는 금액은 허용함.
   *
   * <p>충전 서비스가 지갑 행을 잠근 뒤에 불러야 함. 잠금 안에서 이번 달 누적을 읽으므로, 읽은 뒤에 같은 지갑의 다른 충전이 끼어들 수 없음.
   * 한도 검사는 충전에만 적용하며 WalletService의 입금(credit)에는 넣지 않으므로 캐시백 지급 등 다른 입금은 영향받지 않음.
   *
   * @param wallet 잠금을 잡은 지갑
   * @param amount 충전액(양수)
   * @param now 충전 시각. 월 경계는 이 시각이 속한 달(한국 시간 달력월)로 정함
   * @throws BusinessException 보유 한도 초과(WAL-006), 월 충전 한도 초과(WAL-007)
   */
  @Transactional(readOnly = true)
  public void assertChargeable(Wallet wallet, long amount, LocalDateTime now) {
    // 호출 실수(버그)이므로 내부 방어로 막음. 금액 검증(WAL-003)은 충전 서비스가 먼저 함
    if (amount <= 0) {
      throw new IllegalArgumentException("amount는 양수여야 함: " + amount);
    }

    // 보유 한도: 데브머니 잔액만 봄(캐시백 잔액은 제외).
    // 뺄셈으로 비교해 큰 금액이 들어와도 넘침이 없고, 한도를 낮춰 잔액이 한도보다 커져도 오류 없이 거절됨
    long holdingLimit = operatorSettingService.getLong(SettingKey.WALLET_HOLDING_LIMIT);
    if (amount > holdingLimit - wallet.getBalance()) {
      throw new BusinessException(WalletErrorCode.HOLDING_LIMIT_EXCEEDED);
    }

    // 월 충전 한도: 이번 달 CHARGE 합계만 봄. REFUND는 빼지 않으므로 환불해도 한도가 복원되지 않음
    long monthlyLimit = operatorSettingService.getLong(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT);
    YearMonth month = YearMonth.from(now);
    long charged =
        walletTransactionRepository.sumAmount(
            wallet.getId(),
            WalletTransactionType.CHARGE,
            month.atDay(1).atStartOfDay(),
            month.plusMonths(1).atDay(1).atStartOfDay());
    if (amount > monthlyLimit - charged) {
      throw new BusinessException(WalletErrorCode.MONTHLY_CHARGE_LIMIT_EXCEEDED);
    }
  }
}
