package com.back.nbe1214finalhot6.domain.wallet.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantWallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MerchantWalletRepository extends JpaRepository<MerchantWallet, Long> {

  Optional<MerchantWallet> findByMerchantId(Long merchantId);

  // 입금·출금이 같은 지갑을 동시에 바꾸지 못하게 행을 잠그고 읽음
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<MerchantWallet> findWithLockByMerchantId(Long merchantId);
}
