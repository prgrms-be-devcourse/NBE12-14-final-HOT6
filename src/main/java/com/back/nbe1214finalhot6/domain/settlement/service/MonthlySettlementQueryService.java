package com.back.nbe1214finalhot6.domain.settlement.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementResponse;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MonthlySettlementQueryService {

  private static final long MAX_PERIOD_MONTHS = 12;

  private final MonthlySettlementRepository settlementRepository;
  private final MerchantRepository merchantRepository;

  @Transactional(readOnly = true)
  public List<MonthlySettlementResponse> getMonthly(Long merchantId, YearMonth from, YearMonth to) {
    if (from.isAfter(to) || ChronoUnit.MONTHS.between(from, to) >= MAX_PERIOD_MONTHS) {
      throw new BusinessException(SettlementErrorCode.INVALID_MONTH_PERIOD);
    }
    if (!merchantRepository.existsById(merchantId)) {
      throw new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND);
    }
    return settlementRepository
        .findByMerchantIdAndSettlementMonthBetweenOrderBySettlementMonthAsc(
            merchantId, from.atDay(1), to.atDay(1))
        .stream()
        .map(MonthlySettlementResponse::from)
        .toList();
  }
}
