package com.back.nbe1214finalhot6.domain.settlement.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.settlement.dto.DailySettlementResponse;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailySettlementQueryService {

  private static final long MAX_PERIOD_DAYS = 31;

  private final DailySettlementRepository settlementRepository;
  private final MerchantRepository merchantRepository;

  @Transactional(readOnly = true)
  public List<DailySettlementResponse> getDaily(Long merchantId, LocalDate from, LocalDate to) {
    if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_PERIOD_DAYS) {
      throw new BusinessException(SettlementErrorCode.INVALID_PERIOD);
    }
    if (!merchantRepository.existsById(merchantId)) {
      throw new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND);
    }
    return settlementRepository
        .findByMerchantIdAndSettlementDateBetweenOrderBySettlementDateAsc(merchantId, from, to)
        .stream()
        .map(DailySettlementResponse::from)
        .toList();
  }
}
