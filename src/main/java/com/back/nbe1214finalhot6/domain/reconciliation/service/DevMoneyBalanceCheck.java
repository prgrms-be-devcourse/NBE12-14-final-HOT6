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

/** 2번 대사. 모든 지갑의 데브머니 잔액이 데브머니 거래 원장 합과 같은지 본다. 기준일과 상관없이 실행 시점의 상태를 본다. */
@Component
@RequiredArgsConstructor
public class DevMoneyBalanceCheck implements ReconciliationCheck {

  private static final List<WalletTransactionType> DEVMONEY_TYPES =
      Arrays.stream(WalletTransactionType.values()).filter(t -> !t.affectsCashback()).toList();

  private final UserWalletReader walletReader;

  @Override
  public ReconciliationType type() {
    return ReconciliationType.DEVMONEY_BALANCE;
  }

  // 검사 건수와 불일치 목록을 같은 시점의 데이터로 읽는다
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReconciliationOutcome check(LocalDate date) {
    long checked = walletReader.count();
    return new ReconciliationOutcome(
        checked,
        walletReader.findDevMoneyMismatchIds(DEVMONEY_TYPES).stream()
            .map(id -> "walletId=" + id)
            .toList());
  }
}
