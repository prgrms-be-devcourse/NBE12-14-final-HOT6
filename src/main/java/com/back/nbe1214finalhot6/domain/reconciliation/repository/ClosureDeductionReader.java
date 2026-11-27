package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantClosureDeduction;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** 대사가 해지 정산의 달별 차감액을 읽기만 하는 저장소. */
public interface ClosureDeductionReader extends Repository<MerchantClosureDeduction, Long> {

  List<MerchantClosureDeduction> findBySettlementMonth(LocalDate settlementMonth);

  List<MerchantClosureDeduction> findByMerchantIdIn(Collection<Long> merchantIds);
}
