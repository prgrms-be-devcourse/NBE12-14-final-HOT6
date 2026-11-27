package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.AccountLedgerReader;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.ChargeReader;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.UserLedgerReader;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.entity.Charge;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 6번 대사. 기준일의 충전마다 계좌 원장 차감액과 같은 지갑의 원장 입금액이 충전액과 같은지 본다. */
@Component
@RequiredArgsConstructor
public class ChargeTransferCheck implements ReconciliationCheck {

  private final ChargeReader chargeReader;
  private final UserLedgerReader walletLedgerReader;
  private final AccountLedgerReader accountLedgerReader;

  @Override
  public ReconciliationType type() {
    return ReconciliationType.CHARGE_TRANSFER;
  }

  @Override
  public ReconciliationOutcome check(LocalDate date) {
    List<Charge> charges =
        chargeReader.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(
            date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    if (charges.isEmpty()) {
      return new ReconciliationOutcome(0, List.of());
    }
    List<Long> ids = charges.stream().map(Charge::getId).toList();
    Map<Long, WalletTransaction> walletLedger =
        walletLedgerReader.findByTypeAndRefKeyIn(WalletTransactionType.CHARGE, ids).stream()
            .collect(Collectors.toMap(WalletTransaction::getRefKey, Function.identity()));
    Map<Long, AccountTransaction> accountLedger =
        accountLedgerReader.findByTypeAndRefKeyIn(AccountTransactionType.CHARGE, ids).stream()
            .collect(Collectors.toMap(AccountTransaction::getRefKey, Function.identity()));

    List<String> mismatches = new ArrayList<>();
    for (Charge charge : charges) {
      WalletTransaction credit = walletLedger.get(charge.getId());
      AccountTransaction debit = accountLedger.get(charge.getId());
      boolean matched =
          credit != null
              && debit != null
              && Objects.equals(credit.getWalletId(), charge.getWalletId())
              && credit.getAmount() == charge.getAmount()
              && debit.getAmount() == -charge.getAmount();
      if (!matched) {
        mismatches.add("chargeId=" + charge.getId());
      }
    }
    return new ReconciliationOutcome(charges.size(), mismatches);
  }
}
