package com.back.nbe1214finalhot6.domain.wallet.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWalletTransactionType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantWalletTransactionRepository
    extends JpaRepository<MerchantWalletTransaction, Long> {

  Optional<MerchantWalletTransaction> findByTypeAndRefKey(
      MerchantWalletTransactionType type, Long refKey);
}
