package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** 대사가 회원 지갑 원장을 읽기만 하는 저장소. */
public interface UserLedgerReader extends Repository<WalletTransaction, Long> {

  List<WalletTransaction> findByTypeAndRefKeyIn(
      WalletTransactionType type, Collection<Long> refKeys);

  List<WalletTransaction> findByTypeInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
      Collection<WalletTransactionType> types, LocalDateTime start, LocalDateTime end);
}
