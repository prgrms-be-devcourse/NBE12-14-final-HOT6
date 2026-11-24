package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.AccountLedgerReader;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.RefundReader;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.UserLedgerReader;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.entity.Refund;
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

/** 5번 대사. 기준일의 환불마다 같은 지갑의 원장 차감액과 계좌 원장 입금액이 환불액과 같은지 본다. */
@Component
@RequiredArgsConstructor
public class RefundTransferCheck implements ReconciliationCheck {

  private final RefundReader refundReader;
  private final UserLedgerReader walletLedgerReader;
  private final AccountLedgerReader accountLedgerReader;

  @Override
  public ReconciliationType type() {
    return ReconciliationType.REFUND_TRANSFER;
  }

  @Override
  public ReconciliationOutcome check(LocalDate date) {
    List<Refund> refunds =
        refundReader.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(
            date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    if (refunds.isEmpty()) {
      return new ReconciliationOutcome(0, List.of());
    }
    List<Long> ids = refunds.stream().map(Refund::getId).toList();
    Map<Long, WalletTransaction> walletLedger =
        walletLedgerReader.findByTypeAndRefKeyIn(WalletTransactionType.REFUND, ids).stream()
            .collect(Collectors.toMap(WalletTransaction::getRefKey, Function.identity()));
    Map<Long, AccountTransaction> accountLedger =
        accountLedgerReader.findByTypeAndRefKeyIn(AccountTransactionType.REFUND, ids).stream()
            .collect(Collectors.toMap(AccountTransaction::getRefKey, Function.identity()));

    List<String> mismatches = new ArrayList<>();
    for (Refund refund : refunds) {
      WalletTransaction debit = walletLedger.get(refund.getId());
      AccountTransaction credit = accountLedger.get(refund.getId());
      boolean matched =
          debit != null
              && credit != null
              && Objects.equals(debit.getWalletId(), refund.getWalletId())
              && debit.getAmount() == -refund.getAmount()
              && credit.getAmount() == refund.getAmount();
      if (!matched) {
        mismatches.add("refundId=" + refund.getId());
      }
    }
    return new ReconciliationOutcome(refunds.size(), mismatches);
  }
}
