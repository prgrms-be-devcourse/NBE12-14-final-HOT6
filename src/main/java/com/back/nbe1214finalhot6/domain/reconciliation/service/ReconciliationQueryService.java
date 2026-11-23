package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationResultResponse;
import com.back.nbe1214finalhot6.domain.reconciliation.exception.ReconciliationErrorCode;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.ReconciliationResultRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReconciliationQueryService {

  private static final long MAX_PERIOD_DAYS = 31;

  private final ReconciliationResultRepository resultRepository;

  @Transactional(readOnly = true)
  public List<ReconciliationResultResponse> getResults(LocalDate from, LocalDate to) {
    if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_PERIOD_DAYS) {
      throw new BusinessException(ReconciliationErrorCode.INVALID_PERIOD);
    }
    return resultRepository.findByTargetDateBetweenOrderByIdDesc(from, to).stream()
        .map(ReconciliationResultResponse::from)
        .toList();
  }
}
