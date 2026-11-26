package com.back.nbe1214finalhot6.domain.merchant.repository;

import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantClosureDeduction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantClosureDeductionRepository
    extends JpaRepository<MerchantClosureDeduction, Long> {

  List<MerchantClosureDeduction> findByMerchantIdOrderBySettlementMonthAsc(Long merchantId);
}
