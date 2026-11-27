package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.CashbackEarningReader;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.UserLedgerReader;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4번 대사(캐시백 적립 건). 기준일에 지급·취소·회수된 적립 건과 기준일에 생긴 적립·회수 원장이 가리키는 적립 건을 대상으로 상태에 맞는 원장 기록이 있는지 본다.
 * 지급 완료는 적립 입금만, 회수는 적립 입금과 회수 차감이 있어야 하고, 그 밖의 상태는 기록이 없어야 한다.
 */
@Component
@RequiredArgsConstructor
public class CashbackEarningCheck implements ReconciliationCheck {

  private static final List<WalletTransactionType> EARNING_TYPES =
      List.of(WalletTransactionType.CASHBACK_EARN, WalletTransactionType.CASHBACK_CLAWBACK);

  private final CashbackEarningReader earningReader;
  private final UserLedgerReader ledgerReader;

  @Override
  public ReconciliationType type() {
    return ReconciliationType.CASHBACK_EARNING;
  }

  // 적립 건과 원장을 따로 읽으므로, 그 사이에 지급·회수가 커밋되어도 어긋나 보이지 않게 한 시점의 데이터로 읽는다
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReconciliationOutcome check(LocalDate date) {
    LocalDateTime start = date.atStartOfDay();
    LocalDateTime end = date.plusDays(1).atStartOfDay();
    Set<Long> ids = new TreeSet<>(earningReader.findIdsChangedBetween(start, end));
    ledgerReader
        .findByTypeInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(EARNING_TYPES, start, end)
        .stream()
        .map(WalletTransaction::getRefKey)
        .filter(Objects::nonNull)
        .forEach(ids::add);
    if (ids.isEmpty()) {
      return new ReconciliationOutcome(0, List.of());
    }

    Map<Long, CashbackEarning> earnings =
        earningReader.findByIdIn(ids).stream()
            .collect(Collectors.toMap(CashbackEarning::getId, Function.identity()));
    Map<Long, WalletTransaction> earns = ledger(WalletTransactionType.CASHBACK_EARN, ids);
    Map<Long, WalletTransaction> clawBacks = ledger(WalletTransactionType.CASHBACK_CLAWBACK, ids);

    List<String> mismatches = new ArrayList<>();
    for (Long id : ids) {
      CashbackEarning earning = earnings.get(id);
      if (earning == null || !matches(earning, earns.get(id), clawBacks.get(id))) {
        mismatches.add("earningId=" + id);
      }
    }
    return new ReconciliationOutcome(ids.size(), mismatches);
  }

  private boolean matches(
      CashbackEarning earning, WalletTransaction earn, WalletTransaction clawBack) {
    EarningStatus status = earning.getStatus();
    boolean paid = status == EarningStatus.PAID || status == EarningStatus.CLAWED_BACK;
    boolean clawedBack = status == EarningStatus.CLAWED_BACK;
    return sameEntry(earn, paid, earning, earning.getAmount())
        && sameEntry(clawBack, clawedBack, earning, -earning.getAmount());
  }

  // 기록이 있어야 하면 같은 지갑·같은 금액인지 보고, 없어야 하면 기록이 없는지 본다
  private boolean sameEntry(
      WalletTransaction entry, boolean expected, CashbackEarning earning, long amount) {
    if (!expected) {
      return entry == null;
    }
    return entry != null
        && Objects.equals(entry.getWalletId(), earning.getWalletId())
        && entry.getAmount() == amount;
  }

  private Map<Long, WalletTransaction> ledger(WalletTransactionType type, Set<Long> ids) {
    return ledgerReader.findByTypeAndRefKeyIn(type, ids).stream()
        .collect(Collectors.toMap(WalletTransaction::getRefKey, Function.identity()));
  }
}
