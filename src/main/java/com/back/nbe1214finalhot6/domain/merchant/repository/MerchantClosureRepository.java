package com.back.nbe1214finalhot6.domain.merchant.repository;

import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantClosure;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantClosureRepository extends JpaRepository<MerchantClosure, Long> {

  Optional<MerchantClosure> findByMerchantId(Long merchantId);
}
