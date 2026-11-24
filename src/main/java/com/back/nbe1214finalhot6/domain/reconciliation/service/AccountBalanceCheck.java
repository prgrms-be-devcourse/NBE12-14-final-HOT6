package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.AccountReader;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 1번 대사. 모든 계좌의 잔액이 계좌 원장 합과 같은지 본다. 기준일과 상관없이 실행 시점의 상태를 본다. */
@Component
@RequiredArgsConstructor
public class AccountBalanceCheck implements ReconciliationCheck {

  private final AccountReader accountReader;

  @Override
  public ReconciliationType type() {
    return ReconciliationType.ACCOUNT_BALANCE;
  }

  // 검사 건수와 불일치 목록을 같은 시점의 데이터로 읽는다
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReconciliationOutcome check(LocalDate date) {
    long checked = accountReader.count();
    return new ReconciliationOutcome(
        checked,
        accountReader.findBalanceMismatchIds().stream().map(id -> "accountId=" + id).toList());
  }
}
