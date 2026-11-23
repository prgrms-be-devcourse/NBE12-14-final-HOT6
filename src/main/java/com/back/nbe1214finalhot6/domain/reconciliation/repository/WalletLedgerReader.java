package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransactionType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** 대사가 가맹점 지갑 원장을 읽기만 하는 저장소. */
public interface WalletLedgerReader extends Repository<MerchantWalletTransaction, Long> {

  List<MerchantWalletTransaction> findByTypeAndRefKeyIn(
      MerchantWalletTransactionType type, Collection<Long> refKeys);
}
