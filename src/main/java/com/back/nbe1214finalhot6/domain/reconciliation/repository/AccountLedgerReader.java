package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** 대사가 모의 계좌 원장을 읽기만 하는 저장소. */
public interface AccountLedgerReader extends Repository<AccountTransaction, Long> {

  List<AccountTransaction> findByTypeAndRefKeyIn(
      AccountTransactionType type, Collection<Long> refKeys);
}
