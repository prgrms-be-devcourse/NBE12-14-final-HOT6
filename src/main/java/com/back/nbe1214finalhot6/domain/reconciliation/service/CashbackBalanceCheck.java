package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.UserWalletReader;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 3번 대사(잔액). 모든 지갑의 캐시백 잔액이 캐시백 거래 원장 합과 같은지 본다. 회수로 잔액이 음수가 되어도 원장에 남으므로 그대로 비교한다. 예산은 보지
 * 않는다.
 */
@Component
@RequiredArgsConstructor
public class CashbackBalanceCheck implements ReconciliationCheck {

  private static final List<WalletTransactionType> CASHBACK_TYPES =
      Arrays.stream(WalletTransactionType.values())
          .filter(WalletTransactionType::affectsCashback)
          .toList();

  private final UserWalletReader walletReader;

  @Override
  public ReconciliationType type() {
    return ReconciliationType.CASHBACK_BALANCE;
  }

  // 검사 건수와 불일치 목록을 같은 시점의 데이터로 읽는다
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReconciliationOutcome check(LocalDate date) {
    long checked = walletReader.count();
    return new ReconciliationOutcome(
        checked,
        walletReader.findCashbackMismatchIds(CASHBACK_TYPES).stream()
            .map(id -> "walletId=" + id)
            .toList());
  }
}
