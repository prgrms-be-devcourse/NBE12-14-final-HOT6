package com.back.nbe1214finalhot6.domain.wallet.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantAccountRepository extends JpaRepository<MerchantAccount, Long> {

  Optional<MerchantAccount> findByMerchantId(Long merchantId);
}
